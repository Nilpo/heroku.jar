package com.heroku.api.request;

import com.heroku.api.*;
import com.heroku.api.connection.Connection;
import com.heroku.api.connection.HttpClientConnection;
import com.heroku.api.exception.RequestFailedException;
import com.heroku.api.http.Http;
import com.heroku.api.request.addon.AddonInstall;
import com.heroku.api.request.addon.AddonList;
import com.heroku.api.request.addon.AppAddonsList;
import com.heroku.api.request.app.AppCreate;
import com.heroku.api.request.app.AppDestroy;
import com.heroku.api.request.app.AppInfo;
import com.heroku.api.request.app.AppList;
import com.heroku.api.request.config.ConfigList;
import com.heroku.api.request.domain.DomainAdd;
import com.heroku.api.request.domain.DomainList;
import com.heroku.api.request.domain.DomainRemove;
import com.heroku.api.request.log.Log;
import com.heroku.api.request.releases.ListReleases;
import com.heroku.api.request.releases.ReleaseInfo;
import com.heroku.api.request.releases.Rollback;
import com.heroku.api.request.sharing.CollabList;
import com.heroku.api.request.sharing.SharingAdd;
import com.heroku.api.request.sharing.SharingRemove;
import com.heroku.api.request.slugs.SlugInfo;
import com.heroku.api.request.stack.StackList;
import com.heroku.api.request.user.UserInfo;
import com.heroku.api.response.Unit;
import org.testng.ITestResult;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.util.RetryAnalyzerCount;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.heroku.api.Heroku.Stack.Cedar14;
import static com.heroku.api.IntegrationTestConfig.CONFIG;
import static com.heroku.api.http.Http.Status.*;
import static org.testng.Assert.*;


/**
 * TODO: Javadoc
 *
 * @author Naaman Newbold
 */
public class RequestIntegrationTest extends BaseRequestIntegrationTest {

    static String apiKey = IntegrationTestConfig.CONFIG.getDefaultUser().getApiKey();

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testAppExistsCommand(App app) throws IOException {
        HerokuAPI api = new HerokuAPI(connection, apiKey);

        final String nonExistentApp = "NO-APP-" + System.currentTimeMillis();
        final String existentAppNoAccess = "java";
        final String existentAppWithAccess = app.getName();

        assertTrue(api.isAppNameAvailable(nonExistentApp));
        assertFalse(api.isAppNameAvailable(existentAppNoAccess));
        assertFalse(api.isAppNameAvailable(existentAppWithAccess));

        assertTrue(api.appExists(existentAppNoAccess));
        assertTrue(api.appExists(existentAppWithAccess));
        assertFalse(api.appExists(nonExistentApp));
    }

    @Test(retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testCreateAppCommand() throws IOException {
        AppCreate cmd = new AppCreate(new App().on(Cedar14));
        App response = connection.execute(cmd, apiKey);

        assertNotNull(response.getId());
        assertEquals(response.getStack().getName(), Cedar14.value);
        assertFalse(response.isMaintenance());
        assertNull(response.getBuildpackProvidedDescription());
        deleteApp(response.getName());
    }

    @DataProvider
    public Object[][] logParameters() {
        final String appName = getApp().getName();
        return new Object[][]{
                {new Log(appName)},
                {new Log(appName, true)},
                {Log.logFor(appName).tail(false).num(1).getRequest()}
        };
    }

   /* @Test(dataProvider = "logParameters", retryAnalyzer = UnprocessableEntityRetryAnalyzer.class, successPercentage = 10)
    public void testLogCommand(Log log) throws Exception {
        LogStreamResponse logsResponse = connection.execute(log, apiKey);
        assertLogIsReadable(logsResponse);
    }
*/
    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testAppCommand(App app) throws IOException {
        AppInfo cmd = new AppInfo(app.getName());
        App response = connection.execute(cmd, apiKey);
        assertEquals(response.getName(), app.getName());
    }

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testListAppsCommand(App app) throws IOException {
        AppList cmd = new AppList();
        List<App> response = connection.execute(cmd, apiKey);
        assertNotNull(response);
        assertTrue(response.size() > 0, "At least one app should be present, but there are none.");
    }

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testListAppsWithRangeCommand(App app) throws IOException {
      AppList cmd = new AppList("name ..");
      List<App> response = connection.execute(cmd, apiKey);
      assertNotNull(response);
      assertTrue(response.size() > 0, "At least one app should be present, but there are none.");
    }

    // don't use the app dataprovider because it'll try to delete an already deleted app
    @Test(retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testDestroyAppCommand() throws IOException {
        AppDestroy cmd = new AppDestroy(new HerokuAPI(connection, apiKey).createApp(new App().on(Cedar14)).getName());
        Unit response = connection.execute(cmd, apiKey);
        assertNotNull(response);
    }

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testSharingAddCommand(App app) throws IOException {
        SharingAdd cmd = new SharingAdd(app.getName(), sharingUser.getUsername());
        Unit response = connection.execute(cmd, apiKey);
        assertNotNull(response);
    }

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testSharingAddWithBrandNewUserCommand(App app) throws IOException {
        SharingAdd cmd = new SharingAdd(app.getName(), randomEmail());
        Unit response = connection.execute(cmd, apiKey);
        assertNotNull(response);
    }

    @Test(timeOut = 30000L, retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testSharingTransferCommand() throws IOException {
        assertNotSame(IntegrationTestConfig.CONFIG.getDefaultUser().getUsername(), sharingUser.getUsername());
        HerokuAPI api = new HerokuAPI(IntegrationTestConfig.CONFIG.getDefaultUser().getApiKey());
        App app = api.createApp(new App().on(Cedar14));
        api.addCollaborator(app.getName(), sharingUser.getUsername());
        api.transferApp(app.getName(), sharingUser.getUsername());

        HerokuAPI sharedUserAPI = new HerokuAPI(sharingUser.getApiKey());
        App transferredApp = sharedUserAPI.getApp(app.getName());

        // TODO The transfer must be accepted first
        //assertEquals(transferredApp.getOwnerEmail(), sharingUser.getUsername());
        //sharedUserAPI.destroyApp(transferredApp.getName());
    }

    @Test(dataProvider = "newApp", invocationCount = 5, successPercentage = 20, retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testSharingRemoveCommand(App app) throws IOException {
        SharingAdd sharingAddCommand = new SharingAdd(app.getName(), sharingUser.getUsername());
        Unit sharingAddResp = connection.execute(sharingAddCommand, apiKey);
        assertNotNull(sharingAddResp);

        SharingRemove cmd = new SharingRemove(app.getName(), sharingUser.getUsername());
        Unit response = connection.execute(cmd, apiKey);
        assertNotNull(response);

        CollabList collabList = new CollabList(app.getName());
        assertCollaboratorNotPresent(sharingUser.getUsername(), collabList);
    }

    @Test(retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testConfigAddCommand() throws IOException {
        HerokuAPI api = new HerokuAPI(IntegrationTestConfig.CONFIG.getDefaultUser().getApiKey());
        App app = api.createApp();
        Map<String, String> config = new HashMap<String, String>();
        config.put("FOO", "bar");
        config.put("BAR", "foo");
        config.put("회사", "히로쿠");
        api.updateConfig(app.getName(), config);
        Map<String, String> retrievedConfig = api.listConfig(app.getName());
        assertEquals(retrievedConfig.get("FOO"), "bar");
        assertEquals(retrievedConfig.get("BAR"), "foo");
        assertEquals(retrievedConfig.get("회사"), "히로쿠");
    }

    @Test(dataProvider = "app", invocationCount = 4, successPercentage = 25, retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testConfigCommand(App app) {
        addConfig(app, "FOO", "BAR");
        Request<Map<String, String>> req = new ConfigList(app.getName());
        Map<String, String> response = connection.execute(req, apiKey);
        assertNotNull(response.get("FOO"));
        assertEquals(response.get("FOO"), "BAR");
    }

    @Test(retryAnalyzer = GeneralRetryAnalyzer.class)
    public void testListAddons() {
        AddonList req = new AddonList();
        List<Addon> response = connection.execute(req, apiKey);
        assertNotNull(response, "Expected a response from listing addons, but the result is null.");
    }

    @Test(dataProvider = "newApp", retryAnalyzer = GeneralRetryAnalyzer.class)
    public void testListAppAddons(App app) {
        connection.execute(new AddonInstall(app.getName(), "heroku-postgresql"), apiKey);
        Request<List<Addon>> req = new AppAddonsList(app.getName());
        List<Addon> response = connection.execute(req, apiKey);
        assertNotNull(response);
        assertTrue(response.size() > 0, "Expected at least one addon to be present.");
        assertNotNull(response.get(0).getName());
    }

    @Test(dataProvider = "app", retryAnalyzer = GeneralRetryAnalyzer.class)
    public void testAddAddonToApp(App app) {
        AddonInstall req = new AddonInstall(app.getName(), "heroku-postgresql");
        AddonChange response = connection.execute(req, apiKey);
        assertEquals(response.getState(), "provisioned");
    }

    @Test(dataProvider = "newApp", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testCollaboratorList(App app) {
        Request<List<Collaborator>> req = new CollabList(app.getName());
        List<Collaborator> xmlArrayResponse = connection.execute(req, apiKey);
        assertEquals(xmlArrayResponse.size(), 1);
        assertNotNull(xmlArrayResponse.get(0).getEmail());
    }

    @Test(retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testUserInfo() {
        IntegrationTestConfig.TestUser testUser = CONFIG.getDefaultUser();
        Connection userInfoConnection = new HttpClientConnection();
        UserInfo userInfo = new UserInfo();
        User user = userInfoConnection.execute(userInfo, testUser.getApiKey());
        assertEquals(user.getEmail(), testUser.getUsername());
    }

    @Test(dataProvider = "newApp", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testListReleases(App app) {
        List<Release> releases = connection.execute(new ListReleases(app.getName()), apiKey);
        assertEquals(releases.get(0).getVersion(), 1);
        assertEquals(releases.get(0).getDescription(), "Initial release");
    }

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testReleaseInfo(App app) {
        addConfig(app, "releaseTest", "releaseTest"); //ensure a release exists
        List<Release> releases = connection.execute(new ListReleases(app.getName()), apiKey);
        Release releaseInfo = connection.execute(new ReleaseInfo(app.getName(), releases.get(0).getId()), apiKey);
        assertEquals(releaseInfo.getVersion(), releases.get(0).getVersion());
        assertEquals(releaseInfo.getId(), releases.get(0).getId());
    }
    
    @Test(dataProvider = "newApp", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testRollback(App app) {
        addConfig(app, "releaseTest", "releaseTest");
        List<Release> releases = connection.execute(new ListReleases(app.getName()), apiKey);
        Release previousRelease = releases.get(releases.size() - 2);
        Release rollback = connection.execute(new Rollback(app.getName(), previousRelease.getId()), apiKey);
        assertNotSame(rollback.getId(), previousRelease.getId());
        assertNotSame(rollback.getVersion(), previousRelease.getVersion());
    }
    
    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testStackList(App app) {
        List<StackInfo> stacks = connection.execute(new StackList(), apiKey);
        for (StackInfo s : stacks) {
            if (app.getStack().getName().equals(s.getStack().value)) {
                assertTrue(true);
                return;
            }
        }
        fail("Stack list did not contain the app's stack.");
    }

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testAddDomain(App app) {
        String domainName = randomDomain();
        Domain addedDomain = connection.execute(new DomainAdd(app.getName(), domainName), apiKey);
        assertEquals(addedDomain.getHostname(), domainName);
    }

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testDomainList(App app) {
        String domainName = randomDomain();
        connection.execute(new DomainAdd(app.getName(), domainName), apiKey);
        assertDomainIsPresent(app, domainName);
    }

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testRemoveDomain(App app) {
        String domainName = randomDomain();
        connection.execute(new DomainAdd(app.getName(), domainName), apiKey);
        connection.execute(new DomainRemove(app.getName(), domainName), apiKey);
        assertDomainNotPresent(app, domainName);
    }

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testMaintenanceMode(App app) {
        HerokuAPI api = new HerokuAPI(connection, apiKey);
        assertFalse(api.isMaintenanceModeEnabled(app.getName()));
        api.setMaintenanceMode(getApp().getName(), true);
        assertTrue(api.isMaintenanceModeEnabled(app.getName()));
        api.setMaintenanceMode(getApp().getName(), false);
        assertFalse(api.isMaintenanceModeEnabled(app.getName()));
    }

    @Test(dataProvider = "app", retryAnalyzer = InternalServerErrorAnalyzer.class)
    public void testSlugInfo(App app) {
      HerokuAPI api = new HerokuAPI(connection, apiKey);
      Slug newSlug = api.createSlug(app.getName(), new HashMap<String, String>());
      assertEquals(newSlug.getBlob().getMethod(), "put");
      assertNotNull(newSlug.getBlob().getUrl());
      Slug slugInfo = connection.execute(new SlugInfo(app.getName(), newSlug.getId()), apiKey);
      assertEquals(slugInfo.getId(), newSlug.getId());
      assertEquals(slugInfo.getBlob().getMethod(), "get");
      assertNotNull(slugInfo.getBlob().getUrl());
    }
    
    private void assertDomainIsPresent(App app, String domainName) {
        for (Domain d : connection.execute(new DomainList(app.getName()), apiKey)) {
            if (d.getHostname().equalsIgnoreCase(domainName)) {
                return;
            }
        }
        throw new AssertionError(
            "Domain " + domainName + " should be present, but it was not found for app " + app.getName()
        );
    }

    private void assertDomainNotPresent(App app, String domainName) {
        for (Domain d : connection.execute(new DomainList(app.getName()), apiKey)) {
            if (d.getHostname().equalsIgnoreCase(domainName)) {
                throw new AssertionError(
                    "Domain " + domainName + " should not be present, but it was found for app " + app.getName()
                );
            }
        }
    }

    private String randomEmail() {
        return "herokujartest+" + (int)Math.ceil(Math.random() * 1000000) + "@gmail.com";
    }

    private String randomDomain() {
        return Integer.valueOf((int) Math.ceil(Math.random() * 100000000)) + "-simonwoodstock.com";
    }

    public static class GeneralRetryAnalyzer extends RetryAnalyzerComposition {
        public GeneralRetryAnalyzer() {
            super(UNPROCESSABLE_ENTITY, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE);
        }
    }

    public static class UnprocessableEntityRetryAnalyzer extends RetryAnalyzerComposition {
        public UnprocessableEntityRetryAnalyzer() {
            super(UNPROCESSABLE_ENTITY);
        }
    }

    public static class InternalServerErrorAnalyzer extends RetryAnalyzerComposition {
        public InternalServerErrorAnalyzer() {
            super(INTERNAL_SERVER_ERROR);
        }
    }

    public static abstract class RetryAnalyzerComposition extends RetryAnalyzerCount {

        private final Http.Status[] statuses;

        public RetryAnalyzerComposition(Http.Status status, Http.Status... moreStatuses) {
            statuses = new Http.Status[1 + moreStatuses.length];
            statuses[0] = status;
            System.arraycopy(moreStatuses, 0, statuses, 1, moreStatuses.length);
            setCount(5);
        }

        @Override
        public boolean retryMethod(ITestResult result) {
            if (result.isSuccess()) {
                return false;
            }

            final Throwable testException = result.getThrowable();
            final RequestFailedException requestFailedException;

            if (testException instanceof RequestFailedException) {
                requestFailedException = (RequestFailedException) testException;
                for (Http.Status status : statuses) {
                    if (status.equals(requestFailedException.getStatusCode())) {
                        result.setStatus(ITestResult.SKIP);
                        return true;
                    }
                }
            }

            return false;
        }
    }
}
