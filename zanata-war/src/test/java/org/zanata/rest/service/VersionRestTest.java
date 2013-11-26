package org.zanata.rest.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import org.zanata.ZanataRestTest;
import org.zanata.rest.client.IVersionResource;
import org.zanata.rest.dto.VersionInfo;

public class VersionRestTest extends ZanataRestTest {
    String vVar = "1.0SNAPSHOT";
    String vBuild = "20101009";
    VersionInfo ver = new VersionInfo(vVar, vBuild);
    private final Logger log = LoggerFactory.getLogger(VersionRestTest.class);

    @Override
    protected void prepareResources() {
        VersionService service = new VersionService(ver);
        resources.add(service);
    }

    @Override
    protected void prepareDBUnitOperations() {
    }

    @Test
    public void retrieveVersionInfo() {
        IVersionResource resource;
        log.debug("setup test version service");
        resource =
                getClientRequestFactory().createProxy(IVersionResource.class);

        VersionInfo entity = resource.get().getEntity();
        assertThat(entity.getVersionNo(), is(vVar));
        assertThat(entity.getBuildTimeStamp(), is(vBuild));
    }

}
