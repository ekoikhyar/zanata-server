package org.zanata.action;

import java.io.Serializable;
import java.util.List;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.queryParser.ParseException;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.AutoCreate;
import org.jboss.seam.annotations.Begin;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.zanata.common.EntityStatus;
import org.zanata.dao.ProjectDAO;
import org.zanata.model.HProject;
import org.zanata.security.ZanataIdentity;

@Name("projectSearch")
@Scope(ScopeType.CONVERSATION)
@AutoCreate
public class ProjectSearch implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    @Setter
    private int pageSize = 30;

    @Getter
    @Setter
    private String searchQuery;

    @Getter
    @Setter
    private List<HProject> searchResults;

    @Getter
    private int currentPage = 1;

    @Getter
    private int resultSize;

    @Getter
    @Setter
    private boolean includeObsolete;

    @In
    private ProjectDAO projectDAO;

    @In
    private ZanataIdentity identity;

    public void setCurrentPage(int page) {
        if (page < 1)
            this.currentPage = 1;
        else
            this.currentPage = page;
    }

    public List<HProject> suggestProjects(String query) {
        try {
            return projectDAO.searchQuery(query, 5, 0);
        } catch (ParseException pe) {
            return Lists.newArrayList();
        }
    }

    @Begin(join = true)
    public void search() {
        if (StringUtils.isEmpty(searchQuery)) {
            return;
        }

        try {
            searchResults =
                    projectDAO.searchQuery(searchQuery, pageSize + 1, pageSize
                            * (currentPage - 1));
        } catch (ParseException pe) {
            return;
        }
        // Manually filtering collection as status field is not indexed by
        // hibernate search
        if (!identity.hasPermission("HProject", "view-obsolete")) {
            CollectionUtils.filter(searchResults, new Predicate() {
                @Override
                public boolean evaluate(Object arg0) {
                    return ((HProject) arg0).getStatus() != EntityStatus.OBSOLETE;
                }
            });
        }
        resultSize = searchResults.size();
    }
}
