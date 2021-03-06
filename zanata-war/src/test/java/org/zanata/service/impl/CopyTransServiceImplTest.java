/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.service.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dbunit.operation.DatabaseOperation;
import org.jboss.seam.security.management.JpaIdentityStore;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.zanata.SlowTest;
import org.zanata.ZanataDbunitJpaTest;
import org.zanata.common.ContentState;
import org.zanata.common.ContentType;
import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.dao.AccountDAO;
import org.zanata.dao.DocumentDAO;
import org.zanata.dao.LocaleDAO;
import org.zanata.dao.ProjectDAO;
import org.zanata.dao.ProjectIterationDAO;
import org.zanata.model.HCopyTransOptions;
import org.zanata.model.HDocument;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.seam.SeamAutowire;
import org.zanata.service.CopyTransService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.zanata.common.ContentState.Approved;
import static org.zanata.common.ContentState.NeedReview;
import static org.zanata.common.ContentState.New;
import static org.zanata.common.ContentState.Translated;
import static org.zanata.model.HCopyTransOptions.ConditionRuleAction;
import static org.zanata.model.HCopyTransOptions.ConditionRuleAction.DOWNGRADE_TO_FUZZY;
import static org.zanata.model.HCopyTransOptions.ConditionRuleAction.IGNORE;
import static org.zanata.model.HCopyTransOptions.ConditionRuleAction.REJECT;
import static org.zanata.service.impl.CopyTransServiceImpl.shouldDowngradeToFuzzy;
import static org.zanata.service.impl.CopyTransServiceImpl.shouldReject;

/**
 * @author Carlos Munoz <a
 *         href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
@Test(groups = { "business-tests" })
public class CopyTransServiceImplTest extends ZanataDbunitJpaTest {
    private SeamAutowire seam = SeamAutowire.instance();
    private final List<ContentState> validTranslatedStates = ImmutableList.of(
            Translated, Approved);

    @Override
    protected void prepareDBUnitOperations() {
        beforeTestOperations.add(new DataSetOperation(
                "org/zanata/test/model/ClearAllTables.dbunit.xml",
                DatabaseOperation.CLEAN_INSERT));
        beforeTestOperations.add(new DataSetOperation(
                "org/zanata/test/model/LocalesData.dbunit.xml",
                DatabaseOperation.CLEAN_INSERT));
        beforeTestOperations.add(new DataSetOperation(
                "org/zanata/test/model/AccountData.dbunit.xml",
                DatabaseOperation.CLEAN_INSERT));
        beforeTestOperations.add(new DataSetOperation(
                "org/zanata/test/model/CopyTransTestData.dbunit.xml",
                DatabaseOperation.CLEAN_INSERT));
    }

    @BeforeMethod
    public void initializeSeam() {
        seam.reset()
                .use("entityManager", getEm())
                .use("session", getSession())
                .use(JpaIdentityStore.AUTHENTICATED_USER,
                        seam.autowire(AccountDAO.class).getByUsername("demo"))
                .useImpl(LocaleServiceImpl.class)
                .useImpl(ValidationServiceImpl.class).ignoreNonResolvable();
    }

    /**
     * Use this test to individually test copy trans scenarios.
     */
    @Test(enabled = false)
    public void individualTest() {

        this.testCopyTrans(new CopyTransExecution(REJECT, IGNORE,
                DOWNGRADE_TO_FUZZY, false, true, false, false, Approved)
                .expectTransState(NeedReview));
    }

    @DataProvider(name = "CopyTrans")
    public Object[][] createCopyTransTestParams() {
        Set<CopyTransExecution> expandedExecutions = generateAllExecutions();

        Object[][] val = new Object[expandedExecutions.size()][1];
        int i = 0;
        for (CopyTransExecution exe : expandedExecutions) {
            val[i++][0] = exe;
        }

        return val;
    }

    @Test
    public void basicDetermineContentState() {
        // An empty rule list should not change the state
        for (ContentState state : validTranslatedStates) {
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.<CopyTransServiceImpl.MatchRulePair> newArrayList(),
                    true, state), is(state));
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.<CopyTransServiceImpl.MatchRulePair> newArrayList(),
                    false, state), is(Translated));
        }
    }

    @Test
    public void contentStateWithIgnoreRule() {
        // If the rule is IGNORE, the state should not change no matter what the
        // result is
        for (ContentState state : validTranslatedStates) {
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            true, IGNORE)), true, state), is(state));
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            false, IGNORE)), true, state), is(state));
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            true, IGNORE)), false, state), is(Translated));
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            false, IGNORE)), false, state), is(Translated));
        }
    }

    @Test
    public void contentStateWithRejectRule() {
        // If the rule is Reject, then the match should be rejected only when
        // the evaluation fails
        for (ContentState state : validTranslatedStates) {
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            true, REJECT)), true, state), is(state));
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            false, REJECT)), true, state), is(New));
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            true, REJECT)), false, state), is(Translated));
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            false, REJECT)), false, state), is(New));
        }
    }

    @Test
    public void contentStateWithDowngradeRule() {
        // If the rule is downgrade, then the match should be downgraded when
        // the evaluation fails
        for (ContentState state : validTranslatedStates) {
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            true, DOWNGRADE_TO_FUZZY)), true, state), is(state));
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            false, DOWNGRADE_TO_FUZZY)), true, state),
                    is(NeedReview));
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            true, DOWNGRADE_TO_FUZZY)), false, state),
                    is(Translated));
            assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                    Lists.newArrayList(new CopyTransServiceImpl.MatchRulePair(
                            false, DOWNGRADE_TO_FUZZY)), false, state),
                    is(NeedReview));
        }
    }

    @Test
    public void failedRejectionRule() {
        // A single rejection should reject the whole translation no matter what
        // the other rules say
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(Lists
                .newArrayList(new CopyTransServiceImpl.MatchRulePair(false,
                        DOWNGRADE_TO_FUZZY),
                        new CopyTransServiceImpl.MatchRulePair(false, REJECT)),
                true, Translated), is(New));
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(Lists
                .newArrayList(new CopyTransServiceImpl.MatchRulePair(true,
                        IGNORE), new CopyTransServiceImpl.MatchRulePair(false,
                        REJECT)), false, Translated), is(New));

        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(Lists
                .newArrayList(new CopyTransServiceImpl.MatchRulePair(false,
                        REJECT), new CopyTransServiceImpl.MatchRulePair(false,
                        DOWNGRADE_TO_FUZZY)), true, Translated), is(New));
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(Lists
                .newArrayList(new CopyTransServiceImpl.MatchRulePair(false,
                        REJECT), new CopyTransServiceImpl.MatchRulePair(true,
                        IGNORE)), false, Translated), is(New));
    }

    @Test
    public void failedDowngradeRule() {
        // A failed Downgrade rule should cause the content state to be fuzzy in
        // all cases, except if a rejection is encountered
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(Lists
                .newArrayList(new CopyTransServiceImpl.MatchRulePair(false,
                        DOWNGRADE_TO_FUZZY),
                        new CopyTransServiceImpl.MatchRulePair(true, REJECT)),
                true, Translated), is(NeedReview));
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(Lists
                .newArrayList(new CopyTransServiceImpl.MatchRulePair(false,
                        DOWNGRADE_TO_FUZZY),
                        new CopyTransServiceImpl.MatchRulePair(true, REJECT)),
                false, Translated), is(NeedReview));
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(Lists
                .newArrayList(new CopyTransServiceImpl.MatchRulePair(false,
                        DOWNGRADE_TO_FUZZY),
                        new CopyTransServiceImpl.MatchRulePair(false, IGNORE)),
                true, Approved), is(NeedReview));
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(Lists
                .newArrayList(new CopyTransServiceImpl.MatchRulePair(false,
                        DOWNGRADE_TO_FUZZY),
                        new CopyTransServiceImpl.MatchRulePair(false, IGNORE)),
                true, Approved), is(NeedReview));
    }

    @Test
    public void determineContentStateFromRuleListBasics() {
        // Tests the expected content state when approval is/is not required,
        // and NO rules are evaluated
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                Lists.<CopyTransServiceImpl.MatchRulePair> newArrayList(),
                true, Translated), is(Translated));
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                Lists.<CopyTransServiceImpl.MatchRulePair> newArrayList(),
                false, Translated), is(Translated));
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                Lists.<CopyTransServiceImpl.MatchRulePair> newArrayList(),
                true, Approved), is(Approved));
        assertThat(CopyTransServiceImpl.determineContentStateFromRuleList(
                Lists.<CopyTransServiceImpl.MatchRulePair> newArrayList(),
                false, Approved), is(Translated));
    }

    @Test(dataProvider = "CopyTrans")
    @SlowTest
    public void testCopyTrans(CopyTransExecution execution) {
        // Prepare Execution
        ProjectIterationDAO iterationDAO =
                seam.autowire(ProjectIterationDAO.class);
        LocaleDAO localeDAO = seam.autowire(LocaleDAO.class);

        // Get the project iteration
        HProjectIteration projectIteration;
        if (execution.projectMatches) {
            projectIteration =
                    iterationDAO.getBySlug("same-project", "different-version");
        } else {
            projectIteration =
                    iterationDAO.getBySlug("different-project",
                            "different-version");
        }

        // Set require translation review
        projectIteration
                .setRequireTranslationReview(execution.requireTranslationReview);

        // Change all targets to have the execution's match state
        for (HDocument doc : projectIteration.getDocuments().values()) {
            for (HTextFlow tf : doc.getAllTextFlows().values()) {
                for (HTextFlowTarget tft : tf.getTargets().values()) {
                    tft.setState(execution.matchState);
                }
            }
        }

        // Create the document
        HDocument doc = new HDocument();
        doc.setContentType(ContentType.TextPlain);
        doc.setLocale(localeDAO.findByLocaleId(new LocaleId("en-US")));
        doc.setProjectIteration(projectIteration);
        if (execution.documentMatches) {
            doc.setFullPath("/same/document");
        } else {
            doc.setFullPath("/different/document");
        }
        projectIteration.getDocuments().put(doc.getDocId(), doc);

        // Create the text Flow
        HTextFlow textFlow = new HTextFlow();
        textFlow.setContents("Source Content"); // Source content matches
        textFlow.setPlural(false);
        textFlow.setObsolete(false);
        textFlow.setDocument(doc);
        if (execution.contextMatches) {
            textFlow.setResId("same-context");
        } else {
            textFlow.setResId("different-context");
        }
        doc.getTextFlows().add(textFlow);

        projectIteration = iterationDAO.makePersistent(projectIteration);
        getEm().flush(); // So the rest of the test sees the results

        HCopyTransOptions options =
                new HCopyTransOptions(execution.getContextMismatchAction(),
                        execution.getDocumentMismatchAction(),
                        execution.getProjectMismatchAction());
        CopyTransService copyTransService = seam
                .autowire(CopyTransServiceImpl.class);
        copyTransService.copyTransForIteration(projectIteration, options);

        // Validate execution
        HTextFlow targetTextFlow =
                (HTextFlow) getEm()
                        .createQuery(
                                "from HTextFlow tf where tf.document.projectIteration = :projectIteration "
                                        + "and tf.document.docId = :docId and tf.resId = :resId")
                        .setParameter("projectIteration", projectIteration)
                        .setParameter("docId", doc.getDocId())
                        .setParameter("resId", textFlow.getResId())
                        .getSingleResult();
        HTextFlowTarget target = targetTextFlow.getTargets().get(3L); // Id: 3
                                                                      // for
                                                                      // Locale
                                                                      // de

        if (execution.isExpectUntranslated()) {
            if (target != null && target.getState() != ContentState.New) {
                throw new AssertionError(
                        "Expected untranslated text flow but got state "
                                + target.getState());
            }
        } else if (execution.getExpectedTranslationState() != New) {
            if (target == null) {
                throw new AssertionError("Expected state "
                        + execution.getExpectedTranslationState()
                        + ", but got untranslated.");
            } else if (execution.getExpectedTranslationState() != target
                    .getState()) {
                throw new AssertionError("Expected state "
                        + execution.getExpectedTranslationState()
                        + ", but got " + target.getState());
            }
        }

        // Contents
        if (execution.getExpectedContents() != null) {
            if (target == null) {
                throw new AssertionError("Expected contents "
                        + Arrays.toString(execution.getExpectedContents())
                        + ", but got untranslated.");
            } else if (!Arrays.equals(execution.getExpectedContents(), target
                    .getContents().toArray())) {
                throw new AssertionError("Expected contents "
                        + Arrays.toString(execution.getExpectedContents())
                        + ", but got "
                        + Arrays.toString(target.getContents().toArray()));
            }
        }
    }

    @Test
    public void ignoreTranslationsFromObsoleteProjectAndVersion()
            throws Exception {
        ProjectIterationDAO projectIterationDAO =
                seam.autowire(ProjectIterationDAO.class);
        ProjectDAO projectDAO = seam.autowire(ProjectDAO.class);

        // Make versions and projects obsolete
        HProjectIteration version =
                projectIterationDAO.getBySlug("same-project", "same-version");
        version.setStatus(EntityStatus.OBSOLETE);
        projectIterationDAO.makePersistent(version);

        HProject project = projectDAO.getBySlug("different-project");
        project.setStatus(EntityStatus.OBSOLETE);
        projectDAO.makePersistent(project);

        // Run the copy trans scenario (very liberal, but nothing should be
        // translated)
        CopyTransExecution execution =
                new CopyTransExecution(IGNORE, IGNORE, IGNORE, true, true,
                        true, true, Approved).expectUntranslated();
        testCopyTrans(execution);
    }

    @Test
    public void reuseTranslationsFromObsoleteDocuments() throws Exception {
        ProjectIterationDAO projectIterationDAO =
                seam.autowire(ProjectIterationDAO.class);
        DocumentDAO documentDAO = seam.autowire(DocumentDAO.class);

        // Make all documents obsolete
        HProjectIteration version =
                projectIterationDAO.getBySlug("same-project", "same-version");
        for (HDocument doc : version.getDocuments().values()) {
            doc.setObsolete(true);
            documentDAO.makePersistent(doc);
        }

        ProjectDAO projectDAO = seam.autowire(ProjectDAO.class);
        HProject project = projectDAO.getBySlug("different-project");
        for (HProjectIteration it : project.getProjectIterations()) {
            for (HDocument doc : it.getDocuments().values()) {
                doc.setObsolete(true);
                documentDAO.makePersistent(doc);
            }
        }

        // Run the copy trans scenario (very liberal, but nothing should be
        // translated)
        CopyTransExecution execution =
                new CopyTransExecution(IGNORE, IGNORE, IGNORE, true, true,
                        true, true, Approved).expectTransState(Approved);
        testCopyTrans(execution);
    }

    /**
     * Makes sure that given two equal results, it will reuse the most recent
     * translation.
     */
    @Test
    public void preferLatestTranslation() throws Exception {
        ProjectIterationDAO projectIterationDAO =
                seam.autowire(ProjectIterationDAO.class);
        DocumentDAO documentDAO = seam.autowire(DocumentDAO.class);

        HProjectIteration version =
                projectIterationDAO.getBySlug("same-project", "same-version");

        // Create another version with translations in the same project
        HProjectIteration newerVersion = new HProjectIteration();
        newerVersion.setSlug("newer-version");
        newerVersion.setProject(version.getProject());
        projectIterationDAO.makePersistent(newerVersion);

        // Duplicate the documents (with text flows and translations) on the
        // newer version
        for (HDocument doc : version.getDocuments().values()) {
            HDocument newDoc =
                    new HDocument(doc.getDocId(), doc.getContentType(),
                            doc.getLocale());
            newDoc.setProjectIteration(newerVersion);

            for (HTextFlow tf : doc.getTextFlows()) {
                HTextFlow newTf =
                        new HTextFlow(newDoc, tf.getResId(), tf.getContents()
                                .get(0));

                for (HTextFlowTarget tft : tf.getTargets().values()) {
                    HTextFlowTarget newTft =
                            new HTextFlowTarget(newTf, tft.getLocale());
                    newTft.setContent0(tft.getContents().get(0) + " recent");
                    newTft.setState(Translated);
                    newTf.getTargets().put(newTft.getLocale().getId(), newTft);
                }
                newDoc.getTextFlows().add(newTf);
            }
            documentDAO.makePersistent(newDoc);
        }

        // run copy trans and make sure the latest translation is copied
        CopyTransExecution execution =
                new CopyTransExecution(IGNORE, IGNORE, IGNORE, true, true,
                        true, true, Approved).expectTransState(Translated)
                        .withContents("target-content-de recent");
        testCopyTrans(execution);
    }

    private ContentState getExpectedContentState(CopyTransExecution execution) {
        ContentState expectedContentState =
                execution.getRequireTranslationReview() ? Approved : Translated;

        expectedContentState =
                getExpectedContentState(execution.getContextMatches(),
                        execution.getContextMismatchAction(),
                        expectedContentState);
        expectedContentState =
                getExpectedContentState(execution.getProjectMatches(),
                        execution.getProjectMismatchAction(),
                        expectedContentState);
        expectedContentState =
                getExpectedContentState(execution.getDocumentMatches(),
                        execution.getDocumentMismatchAction(),
                        expectedContentState);
        return expectedContentState;
    }

    public static ContentState getExpectedContentState(boolean match,
            HCopyTransOptions.ConditionRuleAction action,
            ContentState currentState) {
        if (currentState == New) {
            return currentState;
        } else if (shouldReject(match, action)) {
            return New;
        } else if (shouldDowngradeToFuzzy(match, action)) {
            return NeedReview;
        } else {
            return currentState;
        }
    }

    private Set<CopyTransExecution> generateAllExecutions() {
        Set<CopyTransExecution> allExecutions =
                new HashSet<CopyTransExecution>();
        Set<Object[]> paramsSet =
                cartesianProduct(Arrays.asList(ConditionRuleAction.values()),
                        Arrays.asList(ConditionRuleAction.values()),
                        Arrays.asList(ConditionRuleAction.values()),
                        Arrays.asList(true, false), Arrays.asList(true, false),
                        Arrays.asList(true, false), Arrays.asList(true, false),
                        Arrays.asList(Translated, Approved));

        for (Object[] params : paramsSet) {
            CopyTransExecution exec =
                    new CopyTransExecution((ConditionRuleAction) params[0],
                            (ConditionRuleAction) params[1],
                            (ConditionRuleAction) params[2],
                            (Boolean) params[3], (Boolean) params[4],
                            (Boolean) params[5], (Boolean) params[6],
                            (ContentState) params[7]);

            ContentState expectedContentState =
                    this.getExpectedContentState(exec);
            if (expectedContentState == New) {
                exec.expectUntranslated();
            } else {
                exec.expectTransState(expectedContentState).withContents(
                        "target-content-de");
            }
            allExecutions.add(exec);
        }
        return allExecutions;
    }

    /**
     * Utility method to generate a cartesian product of all possible scenarios
     */
    private Set<Object[]> cartesianProduct(Iterable<?>... colls) {
        // Base case
        if (colls.length == 1) {
            Iterable<?> lastSet = colls[0];
            Set<Object[]> product = new HashSet<Object[]>();

            for (Object elem : lastSet) {
                product.add(new Object[] { elem });
            }
            return product;
        }
        // Recursive case
        else {
            Iterable<?> lastSet = colls[colls.length - 1];
            Set<Object[]> subProduct =
                    cartesianProduct(Arrays.copyOfRange(colls, 0,
                            colls.length - 1));
            Set<Object[]> fullProduct = new HashSet<Object[]>();

            for (Object[] subProdElem : subProduct) {
                for (Object elem : lastSet) {
                    Object[] newSubProd =
                            Arrays.copyOf(subProdElem, subProdElem.length + 1);
                    newSubProd[newSubProd.length - 1] = elem;
                    fullProduct.add(newSubProd);
                }
            }

            return fullProduct;
        }
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    private static class CopyTransExecution implements Cloneable {
        private ConditionRuleAction contextMismatchAction;
        private ConditionRuleAction projectMismatchAction;
        private ConditionRuleAction documentMismatchAction;
        private Boolean contextMatches;
        private Boolean projectMatches;
        private Boolean documentMatches;
        private Boolean requireTranslationReview;
        private ContentState expectedTranslationState;
        private boolean expectUntranslated;
        private String[] expectedContents;
        public ContentState matchState;

        private CopyTransExecution(ConditionRuleAction contextMismatchAction,
                ConditionRuleAction projectMismatchAction,
                ConditionRuleAction documentMismatchAction,
                Boolean contextMatches, Boolean projectMatches,
                Boolean documentMatches, Boolean requireTranslationReview,
                ContentState matchState) {
            this.contextMismatchAction = contextMismatchAction;
            this.projectMismatchAction = projectMismatchAction;
            this.documentMismatchAction = documentMismatchAction;
            this.contextMatches = contextMatches;
            this.projectMatches = projectMatches;
            this.documentMatches = documentMatches;
            this.requireTranslationReview = requireTranslationReview;
            this.matchState = matchState;
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        public CopyTransExecution expectTransState(ContentState state) {
            this.expectedTranslationState = state;
            this.expectUntranslated = state == New;
            return this;
        }

        public CopyTransExecution expectUntranslated() {
            this.expectedTranslationState = New;
            this.expectUntranslated = true;
            return this;
        }

        public CopyTransExecution withContents(String... contents) {
            this.expectedContents = contents;
            return this;
        }

        public Collection<CopyTransExecution> expand() {
            Set<CopyTransExecution> expanded =
                    new HashSet<CopyTransExecution>();
            Set<CopyTransExecution> toExpand =
                    new HashSet<CopyTransExecution>();

            toExpand.add(this);

            while (!toExpand.isEmpty()) {
                CopyTransExecution nextExec = toExpand.iterator().next();

                if (nextExec.contextMismatchAction == null) {
                    for (ConditionRuleAction val : ConditionRuleAction.values()) {
                        try {
                            CopyTransExecution expandedEx =
                                    (CopyTransExecution) nextExec.clone();
                            expandedEx.contextMismatchAction = val;
                            toExpand.add(expandedEx);
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else if (nextExec.projectMismatchAction == null) {
                    for (ConditionRuleAction val : ConditionRuleAction.values()) {
                        try {
                            CopyTransExecution expandedEx =
                                    (CopyTransExecution) nextExec.clone();
                            expandedEx.projectMismatchAction = val;
                            toExpand.add(expandedEx);
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else if (nextExec.documentMismatchAction == null) {
                    for (ConditionRuleAction val : ConditionRuleAction.values()) {
                        try {
                            CopyTransExecution expandedEx =
                                    (CopyTransExecution) nextExec.clone();
                            expandedEx.documentMismatchAction = val;
                            toExpand.add(expandedEx);
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else if (nextExec.contextMatches == null) {
                    for (Boolean val : new Boolean[] { Boolean.TRUE,
                            Boolean.FALSE }) {
                        try {
                            CopyTransExecution expandedEx =
                                    (CopyTransExecution) nextExec.clone();
                            expandedEx.contextMatches = val;
                            toExpand.add(expandedEx);
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else if (nextExec.projectMatches == null) {
                    for (Boolean val : new Boolean[] { Boolean.TRUE,
                            Boolean.FALSE }) {
                        try {
                            CopyTransExecution expandedEx =
                                    (CopyTransExecution) nextExec.clone();
                            expandedEx.projectMatches = val;
                            toExpand.add(expandedEx);
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else if (nextExec.documentMatches == null) {
                    for (Boolean val : new Boolean[] { Boolean.TRUE,
                            Boolean.FALSE }) {
                        try {
                            CopyTransExecution expandedEx =
                                    (CopyTransExecution) nextExec.clone();
                            expandedEx.documentMatches = val;
                            toExpand.add(expandedEx);
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else {
                    expanded.add(nextExec);
                }

                toExpand.remove(nextExec);
            }

            return expanded;
        }
    }
}
