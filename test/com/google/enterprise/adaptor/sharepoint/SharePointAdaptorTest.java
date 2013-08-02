// Copyright 2012 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor.sharepoint;

import static com.google.enterprise.adaptor.sharepoint.SharePointAdaptor.FileInfo;
import static com.google.enterprise.adaptor.sharepoint.SharePointAdaptor.HttpClient;
import static com.google.enterprise.adaptor.sharepoint.SiteDataClient.SiteDataFactory;
import static org.junit.Assert.*;

import com.google.common.base.Objects;
import com.google.common.util.concurrent.Callables;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.IOHelper;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.UserPrincipal;
import com.google.enterprise.adaptor.sharepoint.SharePointAdaptor.SiteUserIdMappingCallable;
import com.google.enterprise.adaptor.sharepoint.SharePointAdaptor.UserGroupFactory;

import com.microsoft.schemas.sharepoint.soap.ObjectType;
import com.microsoft.schemas.sharepoint.soap.SPContentDatabase;
import com.microsoft.schemas.sharepoint.soap.SiteDataSoap;
import com.microsoft.schemas.sharepoint.soap.directory.AddUserCollectionToGroup;
import com.microsoft.schemas.sharepoint.soap.directory.AddUserCollectionToRole;
import com.microsoft.schemas.sharepoint.soap.directory.EmailsInputType;
import com.microsoft.schemas.sharepoint.soap.directory.GetAllUserCollectionFromWebResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetCurrentUserInfoResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetGroupCollectionFromRoleResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetGroupCollectionFromSiteResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetGroupCollectionFromUserResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetGroupCollectionFromWebResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetGroupCollectionResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetGroupInfoResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetRoleCollectionFromGroupResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetRoleCollectionFromUserResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetRoleCollectionFromWebResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetRoleCollectionResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetRolesAndPermissionsForCurrentUserResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetRolesAndPermissionsForSiteResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetUserCollection;
import com.microsoft.schemas.sharepoint.soap.directory.GetUserCollectionFromGroupResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetUserCollectionFromRoleResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetUserCollectionFromSiteResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetUserCollectionFromWebResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetUserCollectionResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetUserInfoResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GetUserLoginFromEmailResponse;
import com.microsoft.schemas.sharepoint.soap.directory.GroupsInputType;
import com.microsoft.schemas.sharepoint.soap.directory.PrincipalType;
import com.microsoft.schemas.sharepoint.soap.directory.RemoveUserCollectionFromGroup;
import com.microsoft.schemas.sharepoint.soap.directory.RemoveUserCollectionFromRole;
import com.microsoft.schemas.sharepoint.soap.directory.RemoveUserCollectionFromSite;
import com.microsoft.schemas.sharepoint.soap.directory.RoleOutputType;
import com.microsoft.schemas.sharepoint.soap.directory.RolesInputType;
import com.microsoft.schemas.sharepoint.soap.directory.TrueFalseType;
import com.microsoft.schemas.sharepoint.soap.directory.User;
import com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap;
import com.microsoft.schemas.sharepoint.soap.directory.Users;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;

/**
 * Test cases for {@link SharePointAdaptor}.
 */
public class SharePointAdaptorTest {
  private static final String VS_ENDPOINT
      = "http://localhost:1/_vti_bin/SiteData.asmx";
  private static final ContentExchange VS_CONTENT_EXCHANGE
      = new ContentExchange(ObjectType.VIRTUAL_SERVER, null, null, null,
          true, false, null, loadTestString("vs.xml"));
  private static final ContentExchange CD_CONTENT_EXCHANGE
      = new ContentExchange(ObjectType.CONTENT_DATABASE,
          "{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}", null, null, true, false,
          null, loadTestString("cd.xml"));
  private static final String SITES_SITECOLLECTION_ENDPOINT
      = "http://localhost:1/sites/SiteCollection/_vti_bin/SiteData.asmx";
  private static final SiteAndWebExchange SITES_SITECOLLECTION_SAW_EXCHANGE
      = new SiteAndWebExchange("http://localhost:1/sites/SiteCollection", 0,
          "http://localhost:1/sites/SiteCollection",
          "http://localhost:1/sites/SiteCollection");
  private static final URLSegmentsExchange SITES_SITECOLLECTION_URLSEG_EXCHANGE
      = new URLSegmentsExchange("http://localhost:1/sites/SiteCollection",
          true, null, null, null, null);
  private static final ContentExchange SITES_SITECOLLECTION_S_CONTENT_EXCHANGE
      = new ContentExchange(ObjectType.SITE, null, null, null, true, false,
          null, loadTestString("sites-SiteCollection-s.xml"));
  private static final ContentExchange SITES_SITECOLLECTION_SC_CONTENT_EXCHANGE
      = new ContentExchange(ObjectType.SITE_COLLECTION, null, null, null,
          true, false, null, loadTestString("sites-SiteCollection-sc.xml"));
  private static final URLSegmentsExchange
      SITES_SITECOLLECTION_LISTS_CUSTOMLIST_URLSEG_EXCHANGE
      = new URLSegmentsExchange(
          "http://localhost:1/sites/SiteCollection/Lists/Custom List"
            + "/AllItems.aspx",
          true, null, null, "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", null);
  private static final ContentExchange
      SITES_SITECOLLECTION_LISTS_CUSTOMLIST_L_CONTENT_EXCHANGE
      = new ContentExchange(ObjectType.LIST,
          "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", null, null, false, false,
          null, loadTestString("sites-SiteCollection-Lists-CustomList-l.xml"));
  private static final ContentExchange
      SITES_SITECOLLECTION_LISTS_CUSTOMLIST_F_CONTENT_EXCHANGE
      = new ContentExchange(ObjectType.FOLDER,
          "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", "", null, true, false,
          null, loadTestString("sites-SiteCollection-Lists-CustomList-f.xml"));
  private static final URLSegmentsExchange
      SITES_SITECOLLECTION_LISTS_CUSTOMLIST_1_URLSEG_EXCHANGE
      = new URLSegmentsExchange(
          "http://localhost:1/sites/SiteCollection/Lists/Custom List"
            + "/Test Folder",
          true, null, null, "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", "1");
  private static final URLSegmentsExchange
      SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_URLSEG_EXCHANGE
      = new URLSegmentsExchange(
          "http://localhost:1/sites/SiteCollection/Lists/Custom List"
            + "/Test Folder/2_.000",
          true, null, null, "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", "2");
  private static final ContentExchange
      SITES_SITECOLLECTION_LISTS_CUSTOMLIST_1_LI_CONTENT_EXCHANGE
      = new ContentExchange(ObjectType.LIST_ITEM,
          "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", "", "1", false, false,
          null,
          loadTestString("sites-SiteCollection-Lists-CustomList-1-li.xml"));
  private static final ContentExchange
      SITES_SITECOLLECTION_LISTS_CUSTOMLIST_1_F_CONTENT_EXCHANGE
      = new ContentExchange(ObjectType.FOLDER,
          "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", "Test Folder", null,
          true, false, null,
          loadTestString("sites-SiteCollection-Lists-CustomList-1-f.xml"));
  private static final ContentExchange
      SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_LI_CONTENT_EXCHANGE
      = new ContentExchange(ObjectType.LIST_ITEM,
          "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", "", "2", false, false,
          null,
          loadTestString("sites-SiteCollection-Lists-CustomList-2-li.xml"));
  private static final ContentExchange
      SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_A_CONTENT_EXCHANGE
      = new ContentExchange(ObjectType.LIST_ITEM_ATTACHMENTS,
          "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", "", "2", true, false,
          null,
          loadTestString("sites-SiteCollection-Lists-CustomList-2-a.xml"));

  private final Charset charset = Charset.forName("UTF-8");
  private Config config;
  private SharePointAdaptor adaptor;
  private Callable<ExecutorService> executorFactory
      = new Callable<ExecutorService>() {
        @Override
        public ExecutorService call() {
          return new CallerRunsExecutor();
        }
      };
  private final MockSiteDataFactory initableSiteDataFactory
      = MockSiteDataFactory.blank()
      .endpoint(VS_ENDPOINT, MockSiteData.blank()
          .register(VS_CONTENT_EXCHANGE));

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  /**
   * JAXBContext is expensive to create and is created as part of the class'
   * initialization. Do this in a separately so that the timing for this
   * initalization does not count toward the first real test run. It looks like
   * a bug when a faster test takes longer, just because it ran first.
   */
  @BeforeClass
  public static void initJaxbContext() {
    SharePointAdaptor.init();
  }

  @Before
  public void setup() {
    config = new Config();
    new SharePointAdaptor().initConfig(config);
    config.overrideKey("sharepoint.server", "http://localhost:1");
    config.overrideKey("sharepoint.username", "fakeuser");
    config.overrideKey("sharepoint.password", "fakepass");
  }

  @After
  public void teardown() {
    if (adaptor != null) {
      adaptor.destroy();
    }
  }

  public List<UserPrincipal> users(String... names) {
    List<UserPrincipal> users = new ArrayList<UserPrincipal>();
    for (String name : names) {
      users.add(new UserPrincipal(name));
    }
    return users;
  }

  public List<GroupPrincipal> groups(String... names) {
    List<GroupPrincipal> groups = new ArrayList<GroupPrincipal>();
    for (String name : names) {
      groups.add(new GroupPrincipal(name));
    }
    return groups;
  }
  
  public User createUserGroupUser(long id, String loginName, String sid, 
      String name, String email, boolean isDomainGroup, boolean isSiteAdmin) {
    User u = new User();
    u.setID(id);
    u.setLoginName(loginName);
    u.setSid(sid);
    u.setName(name);
    u.setEmail(email);
    u.setIsDomainGroup(
        isDomainGroup ? TrueFalseType.TRUE : TrueFalseType.FALSE);
    u.setIsSiteAdmin(
        isSiteAdmin ? TrueFalseType.TRUE : TrueFalseType.FALSE);
    return u;        
  }

  @Test
  public void testSiteDataFactoryImpl() throws IOException {
    SiteDataClient.SiteDataFactoryImpl sdfi
        = new SiteDataClient.SiteDataFactoryImpl();
    assertNotNull(
        sdfi.newSiteData("http://localhost:1/_vti_bin/SiteData.asmx"));
    // Test a site with a space.
    assertNotNull(sdfi.newSiteData(
        "http://localhost:1/Site with space/_vti_bin/SiteData.asmx"));
  }

  @Test
  public void testConstructor() {
    new SharePointAdaptor();
  }

  @Test
  public void testNullSiteDataFactory() {
    thrown.expect(NullPointerException.class);
    new SharePointAdaptor(null, new UnsupportedUserGroupFactory(),
        new UnsupportedHttpClient(), executorFactory);
  }
  
  @Test
  public void testNullUserGroupFactory() {
    thrown.expect(NullPointerException.class);
    new SharePointAdaptor(new UnsupportedSiteDataFactory(), null,
        new UnsupportedHttpClient(), executorFactory);
  }

  @Test
  public void testNullHttpClient() {
    thrown.expect(NullPointerException.class);
    new SharePointAdaptor(new UnsupportedSiteDataFactory(),
        new UnsupportedUserGroupFactory(), null, executorFactory);
  }

  @Test
  public void testNullExecutorFactory() {
    thrown.expect(NullPointerException.class);
    new SharePointAdaptor(new UnsupportedSiteDataFactory(),
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(), null);
  }

  @Test
  public void testInitDestroy() throws Exception {
    adaptor = new SharePointAdaptor(initableSiteDataFactory,
        new UnsupportedUserGroupFactory(),
        new UnsupportedHttpClient(), executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    adaptor.destroy();
    adaptor = null;
  }

  @Test
  public void testSpUrlToUriPassthrough() throws Exception {
    assertEquals("http://somehost:1/path/file",
        SharePointAdaptor.spUrlToUri("http://somehost:1/path/file").toString());
  }

  @Test
  public void testSpUrlToUriSpace() throws Exception {
    assertEquals("http://somehost/A%20space",
        SharePointAdaptor.spUrlToUri("http://somehost/A space").toString());
  }

  @Test
  public void testSpUrlToUriPassthroughNoPath() throws Exception {
    assertEquals("https://somehost",
        SharePointAdaptor.spUrlToUri("https://somehost").toString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSpUrlToUriNoSceme() throws Exception {
    SharePointAdaptor.spUrlToUri("http:/");
  }

  @Test
  public void testGetDocContentWrongServer() throws Exception {
    SiteDataFactory siteDataFactory = MockSiteDataFactory.blank()
        .endpoint(VS_ENDPOINT, MockSiteData.blank()
            .register(VS_CONTENT_EXCHANGE)
            .register(new SiteAndWebExchange(
                "http://wronghost:1/", 1, null, null)));

    adaptor = new SharePointAdaptor(siteDataFactory,
        new UnsupportedUserGroupFactory(),
        new UnsupportedHttpClient(), executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://wronghost:1/"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.getDocContent(request, response);
    assertTrue(response.isNotFound());
  }

  @Test
  public void testGetDocContentWrongPage() throws Exception {
    final String wrongPage = "http://localhost:1/wrongPage";
    SiteDataFactory siteDataFactory = MockSiteDataFactory.blank()
        .endpoint(VS_ENDPOINT, MockSiteData.blank()
            .register(VS_CONTENT_EXCHANGE)
            .register(new SiteAndWebExchange(
                wrongPage, 0, "http://localhost:1", "http://localhost:1"))
            .register(new URLSegmentsExchange(
                wrongPage, false, null, null, null, null)));

    adaptor = new SharePointAdaptor(siteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(new DocId(wrongPage));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.getDocContent(request, response);
    assertTrue(response.isNotFound());
  }

  @Test
  public void testGetDocContentVirtualServer() throws Exception {
    SiteDataFactory siteDataFactory = MockSiteDataFactory.blank()
        .endpoint(VS_ENDPOINT, MockSiteData.blank()
            .register(VS_CONTENT_EXCHANGE)
            .register(CD_CONTENT_EXCHANGE));

    adaptor = new SharePointAdaptor(siteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.getDocContent(new GetContentsRequest(new DocId("")), response);
    String responseString = new String(baos.toByteArray(), charset);
    final String golden = "<!DOCTYPE html>\n"
        + "<html><head><title>Virtual Server http://localhost:1/</title></head>"
        + "<body><h1>Virtual Server http://localhost:1/</h1>"
        + "<p>Sites</p><ul>"
        // These are relative URLs to DocIds that are URLs, and thus the "./"
        // prefix is correct.
        + "<li><a href=\"./http://localhost:1\">localhost:1</a></li>"
        + "<li><a href=\"./http://localhost:1/sites/SiteCollection\">"
        + "SiteCollection</a></li>"
        + "</ul></body></html>";
    assertEquals(golden, responseString);
    String[] permit = new String[] {"GDC-PSL\\Administrator",
        "GDC-PSL\\spuser1", "NT AUTHORITY\\LOCAL SERVICE"};
    assertEquals(new Acl.Builder()
        .setEverythingCaseInsensitive()
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setPermitUsers(users(permit)).setPermitGroups(groups(permit)).build(),
        response.getAcl());
    assertNull(response.getDisplayUrl());
  }

  @Test
  public void testGetDocContentSiteCollection() throws Exception {
    SiteDataFactory siteDataFactory = MockSiteDataFactory.blank()
        .endpoint(VS_ENDPOINT, MockSiteData.blank()
            .register(VS_CONTENT_EXCHANGE)
            .register(SITES_SITECOLLECTION_SAW_EXCHANGE))
        .endpoint(SITES_SITECOLLECTION_ENDPOINT, MockSiteData.blank()
            .register(SITES_SITECOLLECTION_URLSEG_EXCHANGE)
            .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE)
            .register(SITES_SITECOLLECTION_SC_CONTENT_EXCHANGE));

    adaptor = new SharePointAdaptor(siteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.getDocContent(request, response);
    String responseString = new String(baos.toByteArray(), charset);
    final String golden = "<!DOCTYPE html>\n"
        + "<html><head><title>Site chinese1</title></head>"
        + "<body><h1>Site chinese1</h1>"
        + "<p>Sites</p>"
        + "<ul><li><a href=\"SiteCollection/somesite\">"
        + "http://localhost:1/sites/SiteCollection/somesite</a></li></ul>"
        + "<p>Lists</p>"
        + "<ul><li><a href=\"SiteCollection/Lists/Announcements/"
        +   "AllItems.aspx\">"
        + "/sites/SiteCollection/Lists/Announcements/AllItems.aspx</a></li>"
        + "<li><a href=\"SiteCollection/Shared%20Documents/Forms/"
        +   "AllItems.aspx\">"
        + "/sites/SiteCollection/Shared Documents/Forms/AllItems.aspx</a>"
        + "</li></ul>"
        + "<p>Folders</p>"
        + "<ul></ul>"
        + "<p>List Items</p>"
        + "<ul><li><a href=\"SiteCollection/default.aspx\">"
        + "default.aspx</a></li></ul>"
        + "</body></html>";
    assertEquals(golden, responseString);
    assertEquals(new Acl.Builder()
        .setEverythingCaseInsensitive()
        .setInheritFrom(new DocId(""))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setPermitGroups(groups("chinese1 Members", "chinese1 Owners",
            "chinese1 Visitors"))
        .setPermitUsers(users("GDC-PSL\\spuser1")).build(),
        response.getAcl());
    assertEquals(URI.create("http://localhost:1/sites/SiteCollection"),
        response.getDisplayUrl());
  }

  @Test
  public void testGetDocContentSiteCollectionWithAdGroup() throws Exception {
    SiteDataFactory siteDataFactory = MockSiteDataFactory.blank()
        .endpoint(VS_ENDPOINT, MockSiteData.blank()
            .register(VS_CONTENT_EXCHANGE)
            .register(SITES_SITECOLLECTION_SAW_EXCHANGE))
        .endpoint(SITES_SITECOLLECTION_ENDPOINT, MockSiteData.blank()
            .register(SITES_SITECOLLECTION_URLSEG_EXCHANGE)
            .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE
              .replaceInContent("Name=\"spuser1\"", "Name=\"GDC-PSL\\group\"")
              .replaceInContent("IsDomainGroup=\"False\"",
                "IsDomainGroup=\"True\""))
            .register(SITES_SITECOLLECTION_SC_CONTENT_EXCHANGE
              .replaceInContent("Name=\"spuser1\"", "Name=\"GDC-PSL\\group\"")
              .replaceInContent("IsDomainGroup=\"False\"",
                "IsDomainGroup=\"True\"")));

    adaptor = new SharePointAdaptor(siteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.getDocContent(request, response);
    assertEquals(new Acl.Builder()
        .setEverythingCaseInsensitive()
        .setInheritFrom(new DocId(""))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setPermitGroups(groups("chinese1 Members", "chinese1 Owners",
            "chinese1 Visitors", "GDC-PSL\\group")).build(),
        response.getAcl());
  }

    @Test
  public void testGetDocContentSiteCollectionWithClaims() throws Exception {
    String permissions = "<permission memberid='11' mask='756052856929' />"
        + "<permission memberid='12' mask='756052856929' />"
        + "<permission memberid='13' mask='756052856929' />"
        + "<permission memberid='14' mask='756052856929' /></permissions>";
    SiteDataFactory siteDataFactory = MockSiteDataFactory.blank()
        .endpoint(VS_ENDPOINT, MockSiteData.blank()
            .register(VS_CONTENT_EXCHANGE)
            .register(SITES_SITECOLLECTION_SAW_EXCHANGE))
        .endpoint(SITES_SITECOLLECTION_ENDPOINT, MockSiteData.blank()
            .register(SITES_SITECOLLECTION_URLSEG_EXCHANGE)
            .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE
              .replaceInContent("</permissions>", permissions))
            .register(SITES_SITECOLLECTION_SC_CONTENT_EXCHANGE
              .replaceInContent("</permissions>", permissions)));

    
    adaptor = new SharePointAdaptor(siteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.getDocContent(request, response);
    assertEquals(new Acl.Builder()
        .setEverythingCaseInsensitive()
        .setInheritFrom(new DocId(""))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setPermitUsers(users("GDC-PSL\\spuser1", "GSA-CONNECTORS\\User1"))
        .setPermitGroups(groups("chinese1 Members", "chinese1 Owners",
            "chinese1 Visitors", "GSA-CONNECTORS\\domain users",
            "Everyone", "NT AUTHORITY\\authenticated users")).build(),
        response.getAcl());
  }

  @Test
  public void testGetDocContentSiteCollectionWithOutOfDateMemberCache()
      throws Exception {
    ReferenceSiteData siteData = new ReferenceSiteData();
    SiteDataFactory siteDataFactory = MockSiteDataFactory.blank()
        .endpoint(VS_ENDPOINT, MockSiteData.blank()
            .register(VS_CONTENT_EXCHANGE)
            .register(SITES_SITECOLLECTION_SAW_EXCHANGE))
        .endpoint(SITES_SITECOLLECTION_ENDPOINT, siteData);
    SiteDataSoap siteDataState1 = MockSiteData.blank()
            .register(SITES_SITECOLLECTION_URLSEG_EXCHANGE)
            .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE)
            .register(SITES_SITECOLLECTION_SC_CONTENT_EXCHANGE);
    SiteDataSoap siteDataState2 = MockSiteData.blank()
            .register(SITES_SITECOLLECTION_URLSEG_EXCHANGE)
            .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE
              .replaceInContent(" memberid='2'", " memberid='100'"))
            .register(SITES_SITECOLLECTION_SC_CONTENT_EXCHANGE
              // Purposefully leave ID=2 alone. The 6 and spuser2 here is simply
              // an otherwise-unused entry.
              .replaceInContent("<User ID=\"6\"", "<User ID=\"100\"")
              .replaceInContent("spuser2", "spuser100"));

    adaptor = new SharePointAdaptor(siteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));

    // This populates the cache, but otherwise doesn't test anything new.
    siteData.setSiteDataSoap(siteDataState1);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.getDocContent(request, response);
    assertEquals(new Acl.Builder()
        .setEverythingCaseInsensitive()
        .setInheritFrom(new DocId(""))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setPermitGroups(groups("chinese1 Members", "chinese1 Owners",
            "chinese1 Visitors"))
        .setPermitUsers(users("GDC-PSL\\spuser1")).build(),
        response.getAcl());

    // Were we able to pick up the new user in the ACLs?
    siteData.setSiteDataSoap(siteDataState2);
    response = new GetContentsResponse(new ByteArrayOutputStream());
    adaptor.getDocContent(request, response);
    assertEquals(new Acl.Builder()
        .setEverythingCaseInsensitive()
        .setInheritFrom(new DocId(""))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setPermitGroups(groups("chinese1 Members", "chinese1 Owners",
            "chinese1 Visitors"))
        .setPermitUsers(users("GDC-PSL\\spuser100")).build(),
        response.getAcl());
  }

  public void testGetDocContentSiteCollectionNoIndex() throws Exception {
    SiteDataFactory siteDataFactory = MockSiteDataFactory.blank()
        .endpoint(VS_ENDPOINT, MockSiteData.blank()
            .register(SITES_SITECOLLECTION_SAW_EXCHANGE))
        .endpoint(SITES_SITECOLLECTION_ENDPOINT, MockSiteData.blank()
            .register(SITES_SITECOLLECTION_URLSEG_EXCHANGE)
            .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE
              .replaceInContent("NoIndex=\"False\"", "NoIndex=\"True\"")));

    adaptor = new SharePointAdaptor(siteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.getDocContent(request, response);
    assertTrue(response.isNotFound());
  }

  @Test
  public void testGetDocContentList() throws Exception {
    SiteDataSoap siteData = MockSiteData.blank()
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_URLSEG_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_L_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_F_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE);
    final MemberIdMapping memberIdMapping;
    {
      Map<Integer, String> users = new HashMap<Integer, String>();
      Map<Integer, String> groups = new HashMap<Integer, String>();
      users.put(1, "GDC-PSL\\administrator");
      groups.put(3, "SiteCollection Owners");
      groups.put(4, "SiteCollection Visitors");
      groups.put(5, "SiteCollection Members");
      memberIdMapping = new MemberIdMapping(users, groups);
    }

    adaptor = new SharePointAdaptor(initableSiteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection/Lists/Custom List/"
          + "AllItems.aspx"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.new SiteAdaptor("http://localhost:1/sites/SiteCollection",
          "http://localhost:1/sites/SiteCollection", siteData,
          new UnsupportedUserGroupSoap(), Callables.returning(memberIdMapping),
          new UnsupportedCallable<MemberIdMapping>())
        .getDocContent(request, response);
    String responseString = new String(baos.toByteArray(), charset);
    final String golden
        = "<!DOCTYPE html>\n"
        + "<html><head><title>List Custom List</title></head>"
        + "<body><h1>List Custom List</h1>"
        + "<p>List Items</p>"
        + "<ul>"
        + "<li><a href=\"3_.000\">Outside Folder</a></li>"
        + "<li><a href=\"Test%20Folder\">Test Folder</a></li>"
        + "</ul></body></html>";
    assertEquals(golden, responseString);
    assertEquals(new Acl.Builder()
        .setEverythingCaseInsensitive()
        .setInheritFrom(new DocId(""))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setPermitGroups(groups("SiteCollection Members",
            "SiteCollection Owners", "SiteCollection Visitors")).build(),
        response.getAcl());
    assertEquals(URI.create("http://localhost:1/sites/SiteCollection/Lists/"
          + "Custom%20List/AllItems.aspx"), response.getDisplayUrl());
  }

  @Test
  public void testGetDocContentListNoIndex() throws Exception {
    SiteDataSoap siteData = MockSiteData.blank()
        .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_URLSEG_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_L_CONTENT_EXCHANGE
          .replaceInContent("NoIndex=\"False\"", "NoIndex=\"True\""));

    adaptor = new SharePointAdaptor(initableSiteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection/Lists/Custom List/"
          + "AllItems.aspx"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.new SiteAdaptor("http://localhost:1/sites/SiteCollection",
          "http://localhost:1/sites/SiteCollection", siteData,
          new UnsupportedUserGroupSoap(),
          new UnsupportedCallable<MemberIdMapping>(),
          new UnsupportedCallable<MemberIdMapping>())
        .getDocContent(request, response);
    assertTrue(response.isNotFound());
  }

  @Test
  public void testGetDocContentAttachment() throws Exception {
    SiteDataSoap siteData = MockSiteData.blank()
        .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_URLSEG_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_L_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_LI_CONTENT_EXCHANGE);
    final String site = "http://localhost:1/sites/SiteCollection";
    final String attachmentId = site + "/Lists/Custom List/Attachments/2/104600"
        + "0.pdf";

    final String goldenContents = "attachment contents";
    final String goldenContentType = "fake/type";
    adaptor = new SharePointAdaptor(initableSiteDataFactory,
        new UnsupportedUserGroupFactory(), new HttpClient() {
      @Override
      public FileInfo issueGetRequest(URL url) {
        assertEquals(
          "http://localhost:1/sites/SiteCollection/Lists/Custom%20List/"
            + "Attachments/2/1046000.pdf",
          url.toString());
        InputStream contents = new ByteArrayInputStream(
            goldenContents.getBytes(charset));
        List<String> headers = Arrays.asList("not-the-Content-Type", "early",
            "conTent-TypE", goldenContentType, "Content-Type", "late");
        return new FileInfo.Builder(contents).setHeaders(headers).build();
      }
    }, executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId(attachmentId));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.new SiteAdaptor("http://localhost:1/sites/SiteCollection",
          "http://localhost:1/sites/SiteCollection", siteData,
          new UnsupportedUserGroupSoap(),
          new UnsupportedCallable<MemberIdMapping>(),
          new UnsupportedCallable<MemberIdMapping>())
        .getDocContent(request, response);
    String responseString = new String(baos.toByteArray(), charset);
    assertEquals(goldenContents, responseString);
    assertEquals(goldenContentType, response.getContentType());
    assertEquals(new Acl.Builder()
        .setInheritFrom(new DocId(
          "http://localhost:1/sites/SiteCollection/Lists/Custom List/"
            + "Test Folder/2_.000"))
        .build(),
        response.getAcl());
    assertEquals(URI.create(
          "http://localhost:1/sites/SiteCollection/Lists/Custom%20List/"
            + "Attachments/2/1046000.pdf"),
        response.getDisplayUrl());
  }

  @Test
  public void testGetDocContentListItem() throws Exception {
    SiteDataSoap siteData = MockSiteData.blank()
        .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_1_URLSEG_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_URLSEG_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_L_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_1_LI_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_LI_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_A_CONTENT_EXCHANGE);
    final MemberIdMapping memberIdMapping;
    {
      Map<Integer, String> users = new HashMap<Integer, String>();
      Map<Integer, String> groups = new HashMap<Integer, String>();
      users.put(1, "GDC-PSL\\administrator");
      groups.put(3, "SiteCollection Owners");
      groups.put(4, "SiteCollection Visitors");
      groups.put(5, "SiteCollection Members");
      memberIdMapping = new MemberIdMapping(users, groups);
    }

    adaptor = new SharePointAdaptor(initableSiteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection/Lists/Custom List/"
          + "Test Folder/2_.000"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.new SiteAdaptor("http://localhost:1/sites/SiteCollection",
          "http://localhost:1/sites/SiteCollection", siteData,
          new UnsupportedUserGroupSoap(), Callables.returning(memberIdMapping),
          new UnsupportedCallable<MemberIdMapping>())
        .getDocContent(request, response);
    String responseString = new String(baos.toByteArray(), charset);
    final String golden
        = "<!DOCTYPE html>\n"
        + "<html><head><title>List Item Inside Folder</title></head>"
        + "<body><h1>List Item Inside Folder</h1>"
        + "<p>Attachments</p><ul>"
        + "<li><a href=\"../Attachments/2/1046000.pdf\">1046000.pdf</a></li>"
        + "</ul></body></html>";
    final Metadata goldenMetadata;
    {
      Metadata meta = new Metadata();
      meta.add("Attachments", "1");
      meta.add("Author", "System Account");
      meta.add("BaseName", "2_");
      meta.add("ContentType", "Item");
      meta.add("ContentTypeId", "0x0100442459C9B5E59C4F9CFDC789A220FC92");
      meta.add("Created", "2012-05-01T22:14:06Z");
      meta.add("Created_x0020_Date", "2012-05-01T22:14:06Z");
      meta.add("Editor", "System Account");
      meta.add("EncodedAbsUrl", "http://localhost:1/sites/SiteCollection/Lists/"
          + "Custom%20List/Test%20Folder/2_.000");
      meta.add("FSObjType", "0");
      meta.add("FileDirRef",
          "sites/SiteCollection/Lists/Custom List/Test Folder");
      meta.add("FileLeafRef", "2_.000");
      meta.add("FileRef",
          "sites/SiteCollection/Lists/Custom List/Test Folder/2_.000");
      meta.add("GUID", "{2C5BEF60-18FA-42CA-B472-7B5E1EC405A5}");
      meta.add("ID", "2");
      meta.add("Last_x0020_Modified", "2012-05-01T22:14:06Z");
      meta.add("LinkFilename", "2_.000");
      meta.add("LinkFilenameNoMenu", "2_.000");
      meta.add("LinkTitle", "Inside Folder");
      meta.add("LinkTitleNoMenu", "Inside Folder");
      meta.add("Modified", "2012-05-04T21:24:32Z");
      meta.add("Order", "200.000000000000");
      meta.add("PermMask", "0x7fffffffffffffff");
      meta.add("ScopeId", "{2E29615C-59E7-493B-B08A-3642949CC069}");
      meta.add("SelectTitle", "2");
      meta.add("ServerRedirected", "0");
      meta.add("ServerUrl",
          "/sites/SiteCollection/Lists/Custom List/Test Folder/2_.000");
      meta.add("Title", "Inside Folder");
      meta.add("UniqueId", "{E7156244-AC2F-4402-AA74-7A365726CD02}");
      meta.add("WorkflowVersion", "1");
      meta.add("_EditMenuTableEnd", "2");
      meta.add("_EditMenuTableStart", "2_.000");
      meta.add("_IsCurrentVersion", "1");
      meta.add("_Level", "1");
      meta.add("_ModerationStatus", "0");
      meta.add("_UIVersion", "512");
      meta.add("_UIVersionString", "1.0");
      meta.add("owshiddenversion", "4");
      goldenMetadata = meta.unmodifiableView();
    }
    assertEquals(golden, responseString);
    assertEquals(goldenMetadata, response.getMetadata());
    assertEquals(new Acl.Builder()
        .setInheritFrom(new DocId("http://localhost:1/sites/SiteCollection/"
            + "Lists/Custom List/Test Folder"))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES).build(),
        response.getAcl());
    assertEquals(URI.create("http://localhost:1/sites/SiteCollection/Lists/"
          + "Custom%20List/DispForm.aspx?ID=2"),
        response.getDisplayUrl());
  }

  @Test
  public void testGetDocContentListItemAnonymousAccess() throws Exception {
    SiteDataSoap siteData = MockSiteData.blank()
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_1_URLSEG_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_URLSEG_EXCHANGE)
        // TODO(ejona): This access of VS doesn't look right, because it should
        // happen on a siteData for VS_ENDPOINT.
        .register(VS_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE
            .replaceInContent("AnonymousPermMask=\"0\"",
              "AnonymousPermMask=\"65536\""))
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_L_CONTENT_EXCHANGE
            .replaceInContent("AllowAnonymousAccess=\"False\"",
              "AllowAnonymousAccess=\"True\"")
            .replaceInContent("AnonymousViewListItems=\"False\"",
              "AnonymousViewListItems=\"True\"")
            .replaceInContent("AnonymousPermMask=\"0\"",
              "AnonymousPermMask=\"68719546465\""))
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_LI_CONTENT_EXCHANGE
            .replaceInContent("ows_Attachments='1'", "ows_Attachments='0'")
            .replaceInContent(
                "ows_ScopeId='2;#{2E29615C-59E7-493B-B08A-3642949CC069}'",
                "ows_ScopeId='2;#{f9cb02b3-7f29-4cac-804f-ba6e14f1eb39}'"));
    final MemberIdMapping memberIdMapping;
    {
      Map<Integer, String> users = new HashMap<Integer, String>();
      Map<Integer, String> groups = new HashMap<Integer, String>();
      users.put(1, "GDC-PSL\\administrator");
      groups.put(3, "SiteCollection Owners");
      groups.put(4, "SiteCollection Visitors");
      groups.put(5, "SiteCollection Members");
      memberIdMapping = new MemberIdMapping(users, groups);
    }

    adaptor = new SharePointAdaptor(initableSiteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection/Lists/Custom List/"
          + "Test Folder/2_.000"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.new SiteAdaptor("http://localhost:1/sites/SiteCollection",
          "http://localhost:1/sites/SiteCollection", siteData,
          new UnsupportedUserGroupSoap(), Callables.returning(memberIdMapping),
          new UnsupportedCallable<MemberIdMapping>())
        .getDocContent(request, response);
    assertNull(response.getAcl());
  }

  @Test
  public void testGetDocContentListItemWithReadSecurity() throws Exception {
    SiteDataSoap siteData = MockSiteData.blank()
        .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_URLSEG_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_L_CONTENT_EXCHANGE
            .replaceInContent("ReadSecurity=\"1\"", "ReadSecurity=\"2\""))
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_2_LI_CONTENT_EXCHANGE
            .replaceInContent("ows_Attachments='1'", "ows_Attachments='0'"));
    final MemberIdMapping memberIdMapping;
    {
      Map<Integer, String> users = new HashMap<Integer, String>();
      Map<Integer, String> groups = new HashMap<Integer, String>();
      users.put(1, "GDC-PSL\\administrator");
      groups.put(3, "SiteCollection Owners");
      groups.put(4, "SiteCollection Visitors");
      groups.put(5, "SiteCollection Members");
      memberIdMapping = new MemberIdMapping(users, groups);
    }

    Users users = new Users();
    users.getUser().add(createUserGroupUser(1, "GDC-PSL\\administrator",
        "S-1-5-21-7369146", "Administrator", "admin@domain.com", false, true));
    users.getUser().add(createUserGroupUser(7, "GDC-PSL\\User1",
        "S-1-5-21-736911", "User1", "User1@domain.com", false, false));
    users.getUser().add(createUserGroupUser(9, "GDC-PSL\\User11",
        "S-1-5-21-7369132", "User11", "User11@domain.com", false, false));
    users.getUser().add(createUserGroupUser(1073741823, "System.Account",
        "S-1-5-21-7369343", "System Account", "System.Account@domain.com",
        false, true));

    MockUserGroupFactory mockUserGroupFactory
        = new MockUserGroupFactory(users);

    adaptor = new SharePointAdaptor(
        initableSiteDataFactory
          .endpoint(SITES_SITECOLLECTION_ENDPOINT, new UnsupportedSiteData()),
        mockUserGroupFactory, new UnsupportedHttpClient(), executorFactory);
    final AccumulatingDocIdPusher docIdPusher = new AccumulatingDocIdPusher();
    adaptor.init(new MockAdaptorContext(config, null) {
      @Override
      public DocIdPusher getDocIdPusher() {
        return docIdPusher;
      }
    });
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection/Lists/Custom List/"
            + "Test Folder/2_.000"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.new SiteAdaptor("http://localhost:1/sites/SiteCollection",
          "http://localhost:1/sites/SiteCollection", siteData,
          mockUserGroupFactory.newUserGroup(
              "http://localhost:1/sites/SiteCollection"),
          Callables.returning(memberIdMapping),
          adaptor.new SiteUserIdMappingCallable(
              "http://localhost:1/sites/SiteCollection"))
        .getDocContent(request, response);
    String responseString = new String(baos.toByteArray(), charset);
    final String golden = "<!DOCTYPE html>\n"
        + "<html><head><title>List Item Inside Folder</title></head>"
        + "<body><h1>List Item Inside Folder</h1>"
        + "</body></html>";

    assertEquals(golden, responseString);
    assertEquals(new Acl.Builder()
        .setEverythingCaseInsensitive()
        .setInheritFrom(new DocId("http://localhost:1/sites/SiteCollection"
            + "/Lists/Custom List/Test Folder/2_.000_READ_SECURITY"))
        .setPermitUsers(users("GDC-PSL\\administrator"))
        .setPermitGroups(groups("SiteCollection Owners",
            "SiteCollection Members", "SiteCollection Visitors"))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES).build(),
        response.getAcl());
    assertEquals(Collections.singletonList(Collections.singletonMap(
        new DocId("http://localhost:1/sites/SiteCollection/Lists/Custom List/"
            + "Test Folder/2_.000_READ_SECURITY"),
        new Acl.Builder()
            .setEverythingCaseInsensitive()
            .setPermitUsers(users("GDC-PSL\\administrator", "System.Account"))
            .setPermitGroups(groups("SiteCollection Owners"))
            .setInheritanceType(Acl.InheritanceType.AND_BOTH_PERMIT)
            .setInheritFrom(new DocId(""))
            .build())),
        docIdPusher.getNamedResources());
  }

  public void testGetDocContentListItemScopeSameAsParent() throws Exception {
    SiteDataSoap siteData = MockSiteData.blank()
        .register(new URLSegmentsExchange(
            "http://localhost:1/sites/SiteCollection/Lists/Custom List"
              + "/Test Folder/2_.000",
            true, null, null, "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", "2"))
        .register(new ContentExchange(ObjectType.LIST_ITEM,
              "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", null, "2", true, false,
              null, loadTestString("tapasnay-Lists-Announcements-1-li.xml")))
        .register(new ContentExchange(ObjectType.LIST,
              "{6F33949A-B3FF-4B0C-BA99-93CB518AC2C0}", null, null, false,
              false, null,
              loadTestString("tapasnay-Lists-Announcements-l.xml")));
    final MemberIdMapping memberIdMapping;
    {
      Map<Integer, String> users = new HashMap<Integer, String>();
      Map<Integer, String> groups = new HashMap<Integer, String>();
      users.put(1, "SOMEHOST\\administrator");
      memberIdMapping = new MemberIdMapping(users, groups);
    }

    adaptor = new SharePointAdaptor(initableSiteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection/Lists/Custom List/"
          + "Test Folder/2_.000"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.new SiteAdaptor("http://localhost:1/sites/SiteCollection",
        "http://localhost:1/sites/SiteCollection",
        siteData, new UnsupportedUserGroupSoap(),
        Callables.returning(memberIdMapping),
        new UnsupportedCallable<MemberIdMapping>())
        .getDocContent(request, response);
    // It looks odd that nobody can access the document since there are no
    // groups and users, but the policy permits GDC-PSL\administrator. Thus, the
    // policy's PARENT_OVERRIDE behavior is important.
    assertEquals(new Acl.Builder()
        .setInheritFrom(new DocId(
            "http://localhost:1/tapasnay/Lists/Announcements"))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES).build(),
        response.getAcl());
  }

  @Test
  public void testGetDocContentFolder() throws Exception {
    SiteDataSoap siteData = MockSiteData.blank()
        .register(SITES_SITECOLLECTION_S_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_1_URLSEG_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_L_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_1_LI_CONTENT_EXCHANGE)
        .register(SITES_SITECOLLECTION_LISTS_CUSTOMLIST_1_F_CONTENT_EXCHANGE);
    final MemberIdMapping memberIdMapping;
    {
      Map<Integer, String> users = new HashMap<Integer, String>();
      Map<Integer, String> groups = new HashMap<Integer, String>();
      users.put(1, "GDC-PSL\\administrator");
      groups.put(3, "SiteCollection Owners");
      groups.put(4, "SiteCollection Visitors");
      groups.put(5, "SiteCollection Members");
      memberIdMapping = new MemberIdMapping(users, groups);
    }

    adaptor = new SharePointAdaptor(initableSiteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    adaptor.init(new MockAdaptorContext(config, null));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GetContentsRequest request = new GetContentsRequest(
        new DocId("http://localhost:1/sites/SiteCollection/Lists/Custom List/"
          + "Test Folder"));
    GetContentsResponse response = new GetContentsResponse(baos);
    adaptor.new SiteAdaptor("http://localhost:1/sites/SiteCollection",
          "http://localhost:1/sites/SiteCollection",
          siteData, new UnsupportedUserGroupSoap(),
        Callables.returning(memberIdMapping),
        new UnsupportedCallable<MemberIdMapping>())
        .getDocContent(request, response);
    String responseString = new String(baos.toByteArray(), charset);
    final String golden
        = "<!DOCTYPE html>\n"
        + "<html><head><title>Folder Test Folder</title></head>"
        + "<body><h1>Folder Test Folder</h1>"
        + "<p>List Items</p>"
        + "<ul>"
        + "<li><a href=\"Test%20Folder/2_.000\">Inside Folder</a></li>"
        + "<li><a href=\"Test%20Folder/testing\">testing</a></li>"
        + "</ul></body></html>";
    final Metadata goldenMetadata;
    {
      Metadata meta = new Metadata();
      meta.add("Attachments", "0");
      meta.add("Author", "System Account");
      meta.add("BaseName", "Test Folder");
      meta.add("ContentType", "Folder");
      meta.add("ContentTypeId", "0x01200077DD29735CE61148A73F540231F24430");
      meta.add("Created", "2012-05-01T22:13:47Z");
      meta.add("Created_x0020_Date", "2012-05-01T22:13:47Z");
      meta.add("Editor", "System Account");
      meta.add("EncodedAbsUrl", "http://localhost:1/sites/SiteCollection/Lists/"
          + "Custom%20List/Test%20Folder");
      meta.add("FSObjType", "1");
      meta.add("FileDirRef", "sites/SiteCollection/Lists/Custom List");
      meta.add("FileLeafRef", "Test Folder");
      meta.add("FileRef", "sites/SiteCollection/Lists/Custom List/Test Folder");
      meta.add("GUID", "{C099F4ED-6E96-4A00-B94A-EE443061EE49}");
      meta.add("ID", "1");
      meta.add("Last_x0020_Modified", "2012-05-02T21:13:17Z");
      meta.add("LinkFilename", "Test Folder");
      meta.add("LinkFilenameNoMenu", "Test Folder");
      meta.add("LinkTitle", "Test Folder");
      meta.add("LinkTitleNoMenu", "Test Folder");
      meta.add("Modified", "2012-05-01T22:13:47Z");
      meta.add("Order", "100.000000000000");
      meta.add("PermMask", "0x7fffffffffffffff");
      meta.add("ScopeId", "{2E29615C-59E7-493B-B08A-3642949CC069}");
      meta.add("SelectTitle", "1");
      meta.add("ServerRedirected", "0");
      meta.add("ServerUrl",
          "/sites/SiteCollection/Lists/Custom List/Test Folder");
      meta.add("Title", "Test Folder");
      meta.add("UniqueId", "{CE33B6B7-9F5E-4224-8D77-9C42E6290FE6}");
      meta.add("WorkflowVersion", "1");
      meta.add("_EditMenuTableEnd", "1");
      meta.add("_EditMenuTableStart", "Test Folder");
      meta.add("_IsCurrentVersion", "1");
      meta.add("_Level", "1");
      meta.add("_ModerationStatus", "0");
      meta.add("_UIVersion", "512");
      meta.add("_UIVersionString", "1.0");
      meta.add("owshiddenversion", "1");
      goldenMetadata = meta.unmodifiableView();
    }
    assertEquals(golden, responseString);
    assertEquals(goldenMetadata, response.getMetadata());
    assertEquals(new Acl.Builder()
        .setEverythingCaseInsensitive()
        .setInheritFrom(new DocId(""))
        .setInheritanceType(Acl.InheritanceType.PARENT_OVERRIDES)
        .setPermitGroups(groups("SiteCollection Members",
            "SiteCollection Owners", "SiteCollection Visitors"))
        .setPermitUsers(users("GDC-PSL\\administrator")).build(),
        response.getAcl());
    assertEquals(URI.create("http://localhost:1/sites/SiteCollection/Lists/"
          + "Custom%20List/AllItems.aspx?RootFolder=/sites/SiteCollection/"
          + "Lists/Custom%20List/Test%20Folder"),
        response.getDisplayUrl());
  }

  @Test
  public void testGetDocIds() throws Exception {
    adaptor = new SharePointAdaptor(initableSiteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    adaptor.init(new MockAdaptorContext(config, pusher));
    assertEquals(0, pusher.getRecords().size());
    adaptor.getDocIds(pusher);
    assertEquals(1, pusher.getRecords().size());
    assertEquals(new DocIdPusher.Record.Builder(new DocId("")).build(),
        pusher.getRecords().get(0));
  }

  @Test
  public void testModifiedGetDocIds() throws Exception {
    final String getContentContentDatabase4fb
        = "<ContentDatabase>"
        + "<Metadata ChangeId=\"1;0;4fb7dea1-2912-4927-9eda-1ea2f0977cf8;634727"
        +   "056594000000;603\""
        + " ID=\"{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}\" />"
        + "</ContentDatabase>";
    final String getContentContentDatabase3ac
        = "<ContentDatabase>"
        + "<Metadata ChangeId=\"1;0;3ac1e3b3-2326-7341-4afe-16751eafbc51;634882"
        +   "028739000000;224\""
        + " ID=\"{3ac1e3b3-2326-7341-4afe-16751eafbc51}\" />"
        + "</ContentDatabase>";
    final String getChangesContentDatabase4fb
        = "<SPContentDatabase Change=\"Unchanged\" ItemCount=\"0\">"
        + "<ContentDatabase>"
        + "<Metadata ChangeId=\"1;0;4fb7dea1-2912-4927-9eda-1ea2f0977cf8;634727"
        +   "056594000000;603\""
        + " ID=\"{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}\" />"
        + "</ContentDatabase></SPContentDatabase>";
    final ReferenceSiteData siteData = new ReferenceSiteData();
    SiteDataSoap state0 = MockSiteData.blank()
        .register(VS_CONTENT_EXCHANGE);
    SiteDataSoap state1 = new UnsupportedSiteData() {
      @Override
      public void getContent(ObjectType objectType, String objectId,
          String folderUrl, String itemId, boolean retrieveChildItems,
          boolean securityOnly, Holder<String> lastItemIdOnPage,
          Holder<String> getContentResult) {
        throw new WebServiceException("fake IO error");
      }
    };
    SiteDataSoap state2 = MockSiteData.blank()
        .register(VS_CONTENT_EXCHANGE.replaceInContent(
          "<ContentDatabase ID=\"{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}\" />",
          "<ContentDatabase ID=\"{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}\" />"
           + "<ContentDatabase ID=\"{3ac1e3b3-2326-7341-4afe-16751eafbc51}\" />"
          ))
        .register(new ContentExchange(ObjectType.CONTENT_DATABASE,
              "{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}", null, null, false,
              false, null, getContentContentDatabase4fb))
        .register(new ContentExchange(ObjectType.CONTENT_DATABASE,
              "{3ac1e3b3-2326-7341-4afe-16751eafbc51}", null, null, false,
              false, null, getContentContentDatabase3ac));
    SiteDataSoap state3 = MockSiteData.blank()
        .register(VS_CONTENT_EXCHANGE)
        .register(new ChangesExchange(ObjectType.CONTENT_DATABASE,
              "{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}",
              "1;0;4fb7dea1-2912-4927-9eda-1ea2f0977cf8;634727056594000000;603",
              "1;0;4fb7dea1-2912-4927-9eda-1ea2f0977cf8;634727056594000000;603",
              null,
              "1;0;4fb7dea1-2912-4927-9eda-1ea2f0977cf8;634727056594000000;603",
              15, getChangesContentDatabase4fb, false));
    final AtomicLong atomicNumberGetChangesCalls = new AtomicLong(0);
    SiteDataSoap countingSiteData = new DelegatingSiteData() {
      @Override
      protected SiteDataSoap delegate() {
        return siteData;
      }

      @Override
      public void getChanges(ObjectType objectType,
          String contentDatabaseId, Holder<String> lastChangeId,
          Holder<String> currentChangeId, Integer timeout,
          Holder<String> getChangesResult, Holder<Boolean> moreChanges) {
        atomicNumberGetChangesCalls.getAndIncrement();
        super.getChanges(objectType, contentDatabaseId, lastChangeId,
            currentChangeId, timeout, getChangesResult, moreChanges);
      }
    };
    SiteDataFactory siteDataFactory = MockSiteDataFactory.blank()
        .endpoint(VS_ENDPOINT, countingSiteData);
    adaptor = new SharePointAdaptor(siteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    siteData.setSiteDataSoap(state0);
    adaptor.init(new MockAdaptorContext(config, pusher));

    // Error getting content databases, so content databases remains unchanged
    // (empty).
    siteData.setSiteDataSoap(state1);
    adaptor.getModifiedDocIds(pusher);
    assertEquals(0, pusher.getRecords().size());
    assertEquals(0, atomicNumberGetChangesCalls.get());

    // Find new content databases and get their current change id.
    siteData.setSiteDataSoap(state2);
    adaptor.getModifiedDocIds(pusher);
    assertEquals(1, pusher.getRecords().size());
    assertEquals(new DocIdPusher.Record.Builder(new DocId(""))
        .setCrawlImmediately(true).build(),
        pusher.getRecords().get(0));
    assertEquals(0, atomicNumberGetChangesCalls.get());
    pusher.reset();

    // Discover one content database disappeared; get changes for other content
    // database.
    siteData.setSiteDataSoap(state3);
    adaptor.getModifiedDocIds(pusher);
    assertEquals(1, pusher.getRecords().size());
    assertEquals(new DocIdPusher.Record.Builder(new DocId(""))
        .setCrawlImmediately(true).build(),
        pusher.getRecords().get(0));
    assertEquals(1, atomicNumberGetChangesCalls.get());
  }

  @Test
  public void testModifiedGetDocIdsSP2010() throws Exception {
    final String getContentContentDatabase4fb
        = "<ContentDatabase>"
        + "<Metadata ChangeId=\"1;0;4fb7dea1-2912-4927-9eda-1ea2f0977cf8;634727"
        +   "056594000000;603\""
        + " ID=\"{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}\" />"
        + "</ContentDatabase>";
    final String getChangesContentDatabase4fb
        = "<SPContentDatabase Change=\"Unchanged\" ItemCount=\"0\">"
        + "<ContentDatabase>"
        + "<Metadata ChangeId=\"1;0;4fb7dea1-2912-4927-9eda-1ea2f0977cf9;634727"
        +   "056595000000;604\""
        + " ID=\"{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}\" />"
        + "</ContentDatabase></SPContentDatabase>";
    // SP 2010 provides more metadata than 2007.
    ContentExchange vsContentExchange = VS_CONTENT_EXCHANGE.replaceInContent(
        "<Metadata URL=\"http://localhost:1/\" />",
        "<Metadata ID=\"{3a125232-0c27-495f-8c92-65ad85b5a17c}\""
          + " Version=\"14.0.4762.1000\" URL=\"http://localhost:1/\""
          + " URLZone=\"Default\" URLIsHostHeader=\"False\" />");
    final AtomicLong atomicNumberGetChangesCalls = new AtomicLong(0);
    final SiteDataSoap siteData = MockSiteData.blank()
        .register(vsContentExchange)
        .register(new ContentExchange(ObjectType.CONTENT_DATABASE,
              "{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}", null, null, false,
              false, null, getContentContentDatabase4fb))
        // The timeout in SP 2010 is not a timeout and should always be at least
        // 60 to get a result.
        .register(new ChangesExchange(ObjectType.CONTENT_DATABASE,
              "{4fb7dea1-2912-4927-9eda-1ea2f0977cf8}",
              "1;0;4fb7dea1-2912-4927-9eda-1ea2f0977cf8;634727056594000000;603",
              "1;0;4fb7dea1-2912-4927-9eda-1ea2f0977cf8;634727056594000000;603",
              null,
              "1;0;4fb7dea1-2912-4927-9eda-1ea2f0977cf8;634727056594000000;603",
              600, getChangesContentDatabase4fb, false));
    SiteDataSoap countingSiteData = new DelegatingSiteData() {
      @Override
      protected SiteDataSoap delegate() {
        return siteData;
      }

      @Override
      public void getChanges(ObjectType objectType,
          String contentDatabaseId, Holder<String> lastChangeId,
          Holder<String> currentChangeId, Integer timeout,
          Holder<String> getChangesResult, Holder<Boolean> moreChanges) {
        atomicNumberGetChangesCalls.getAndIncrement();
        super.getChanges(objectType, contentDatabaseId, lastChangeId,
            currentChangeId, timeout, getChangesResult, moreChanges);
      }
    };
    SiteDataFactory siteDataFactory = MockSiteDataFactory.blank().
        endpoint(VS_ENDPOINT, countingSiteData);
    adaptor = new SharePointAdaptor(siteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    adaptor.init(new MockAdaptorContext(config, pusher));

    // Initialize changeIds.
    adaptor.getModifiedDocIds(pusher);
    assertEquals(0, atomicNumberGetChangesCalls.get());

    // Check for changes. This should not go into an infinite loop.
    adaptor.getModifiedDocIds(pusher);
    assertEquals(1, atomicNumberGetChangesCalls.get());
  }

  @Test
  public void testModifiedGetDocIdsClient() throws Exception {
    final String getChangesContentDatabase
        = loadTestString("testModifiedGetDocIdsClient.changes-cd.xml");
    adaptor = new SharePointAdaptor(initableSiteDataFactory,
        new UnsupportedUserGroupFactory(), new UnsupportedHttpClient(),
        executorFactory);
    AccumulatingDocIdPusher pusher = new AccumulatingDocIdPusher();
    adaptor.init(new MockAdaptorContext(config, pusher));
    SPContentDatabase result = parseChanges(getChangesContentDatabase);
    adaptor.new SiteAdaptor(
        "http://localhost:1/sites/SiteCollection",
        "http://localhost:1/sites/SiteCollection", new UnsupportedSiteData(),
        new UnsupportedUserGroupSoap(),
        new UnsupportedCallable<MemberIdMapping>(),
        new UnsupportedCallable<MemberIdMapping>())
        .getModifiedDocIds(result, pusher);
    assertEquals(1, pusher.getRecords().size());
    assertEquals(new DocIdPusher.Record.Builder(new DocId(
          "http://localhost:1/Lists/Announcements/2_.000"))
        .setCrawlImmediately(true).build(), pusher.getRecords().get(0));
  }

  @Test
  public void testParseError() throws Exception {
    SiteDataClient client = new SiteDataClient(
        new UnsupportedSiteData(), false);
    String xml = "<broken";
    thrown.expect(IOException.class);
    client.jaxbParse(xml, SPContentDatabase.class);
  }

  @Test
  public void testValidationError() throws Exception {
    SiteDataClient client = new SiteDataClient(
        new UnsupportedSiteData(), true);
    // Lacks required child element.
    String xml = "<SPContentDatabase"
        + " xmlns='http://schemas.microsoft.com/sharepoint/soap/'/>";
    thrown.expect(IOException.class);
    client.jaxbParse(xml, SPContentDatabase.class);
  }

  @Test
  public void testDisabledValidation() throws Exception {
    SiteDataClient client = new SiteDataClient(
        new UnsupportedSiteData(), false);
    // Lacks required child element.
    String xml = "<SPContentDatabase"
        + " xmlns='http://schemas.microsoft.com/sharepoint/soap/'/>";
    assertNotNull(client.jaxbParse(xml, SPContentDatabase.class));
  }


  @Test
  public void testParseUnknownXml() throws Exception {
    SiteDataClient client = new SiteDataClient(
        new UnsupportedSiteData(), true);
    // Valid XML, but not any class that we know about.
    String xml = "<html/>";
    thrown.expect(IOException.class);
    client.jaxbParse(xml, SPContentDatabase.class);
  }

  @Test
  public void testFileInfoGetFirstHeaderWithNameMissing() {
    FileInfo fi = new FileInfo.Builder(new ByteArrayInputStream(new byte[0]))
        .setHeaders(Arrays.asList("Some-Header", "somevalue")).build();
    assertEquals("somevalue", fi.getFirstHeaderWithName("some-heaDer"));
    assertNull(fi.getFirstHeaderWithName("Missing-Header"));
  }

  @Test
  public void testFileInfoNullContents() {
    thrown.expect(NullPointerException.class);
    new FileInfo.Builder(null);
  }

  @Test
  public void testFileInfoNullHeaders() {
    FileInfo.Builder builder
        = new FileInfo.Builder(new ByteArrayInputStream(new byte[0]));
    thrown.expect(NullPointerException.class);
    builder.setHeaders(null);
  }

  @Test
  public void testFileInfoOddHeadersLength() {
    FileInfo.Builder builder
        = new FileInfo.Builder(new ByteArrayInputStream(new byte[0]));
    thrown.expect(IllegalArgumentException.class);
    builder.setHeaders(Arrays.asList("odd-length"));
  }

  private static <T> void setValue(Holder<T> holder, T value) {
    if (holder != null) {
      holder.value = value;
    }
  }

  private SPContentDatabase parseChanges(String xml) throws IOException {
    SiteDataClient client = new SiteDataClient(new UnsupportedSiteData(), true);
    String xmlns = "http://schemas.microsoft.com/sharepoint/soap/";
    xml = xml.replace("<SPContentDatabase ",
        "<SPContentDatabase xmlns='" + xmlns + "' ");
    return client.jaxbParse(xml, SPContentDatabase.class);
  }

  private static String loadTestString(String testString) {
    try {
      return loadResourceAsString("spresponses/" + testString);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private static String loadResourceAsString(String resource)
      throws IOException {
    return IOHelper.readInputStreamToString(SharePointAdaptorTest.class
        .getResourceAsStream(resource), Charset.forName("UTF-8"));
  }

  private static class UnsupportedSiteDataFactory implements SiteDataFactory {
    @Override
    public SiteDataSoap newSiteData(String endpoint) {
      throw new UnsupportedOperationException();
    }
  }

  private static class UnsupportedUserGroupFactory
      implements UserGroupFactory {
    @Override
    public UserGroupSoap newUserGroup(String endpoint) {
      return new UnsupportedUserGroupSoap();
    }
  }

  private static class MockUserGroupFactory implements UserGroupFactory {
    final Users users;
    public MockUserGroupFactory(Users users) {
      this.users = users;
    }

    @Override
    public UserGroupSoap newUserGroup(String endpoint) {
      return new MockUserGroupSoap(users);
    }
  }

  private static class UnsupportedHttpClient implements HttpClient {
    @Override
    public FileInfo issueGetRequest(URL url) {
      throw new UnsupportedOperationException();
    }
  }

  private static class MockUserGroupSoap extends UnsupportedUserGroupSoap {
    final Users users;    
    public MockUserGroupSoap(Users users) {
      this.users = users;      
    }
    
    @Override
    public GetUserCollectionFromSiteResponse.GetUserCollectionFromSiteResult 
        getUserCollectionFromSite() {
      GetUserCollectionFromSiteResponse.GetUserCollectionFromSiteResult result 
          = new GetUserCollectionFromSiteResponse
              .GetUserCollectionFromSiteResult();
      GetUserCollectionFromSiteResponse
          .GetUserCollectionFromSiteResult.GetUserCollectionFromSite siteUsers 
          = new GetUserCollectionFromSiteResponse
              .GetUserCollectionFromSiteResult.GetUserCollectionFromSite();   
      siteUsers.setUsers(users);
      result.setGetUserCollectionFromSite(siteUsers);
      return result;      
    }
  }
  
  private static class UnsupportedUserGroupSoap implements UserGroupSoap {
    @Override
    public GetUserCollectionFromSiteResponse.GetUserCollectionFromSiteResult 
        getUserCollectionFromSite() {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetUserCollectionFromWebResponse.GetUserCollectionFromWebResult 
        getUserCollectionFromWeb() {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetAllUserCollectionFromWebResponse.GetAllUserCollectionFromWebResult 
        getAllUserCollectionFromWeb() {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetUserCollectionFromGroupResponse.GetUserCollectionFromGroupResult 
        getUserCollectionFromGroup(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetUserCollectionFromRoleResponse.GetUserCollectionFromRoleResult 
        getUserCollectionFromRole(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetUserCollectionResponse.GetUserCollectionResult 
        getUserCollection(GetUserCollection.UserLoginNamesXml ulnx) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetUserInfoResponse.GetUserInfoResult 
        getUserInfo(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetCurrentUserInfoResponse.GetCurrentUserInfoResult 
        getCurrentUserInfo() {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void addUserToGroup(String string, String string1, 
        String string2, String string3, String string4) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void addUserCollectionToGroup(String string,
        AddUserCollectionToGroup.UsersInfoXml uix) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void addUserToRole(String string, String string1,
        String string2, String string3, String string4) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void addUserCollectionToRole(String string,
        AddUserCollectionToRole.UsersInfoXml uix) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void updateUserInfo(String string, String string1,
        String string2, String string3) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void removeUserFromSite(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void removeUserCollectionFromSite(
        RemoveUserCollectionFromSite.UserLoginNamesXml ulnx) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void removeUserFromWeb(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void removeUserFromGroup(String string, String string1) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void removeUserCollectionFromGroup(String string,
        RemoveUserCollectionFromGroup.UserLoginNamesXml ulnx) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void removeUserFromRole(String string, String string1) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void removeUserCollectionFromRole(String string,
        RemoveUserCollectionFromRole.UserLoginNamesXml ulnx) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetGroupCollectionFromSiteResponse.GetGroupCollectionFromSiteResult
        getGroupCollectionFromSite() {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetGroupCollectionFromWebResponse.GetGroupCollectionFromWebResult
        getGroupCollectionFromWeb() {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetGroupCollectionFromRoleResponse.GetGroupCollectionFromRoleResult
        getGroupCollectionFromRole(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetGroupCollectionFromUserResponse.GetGroupCollectionFromUserResult
        getGroupCollectionFromUser(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetGroupCollectionResponse.GetGroupCollectionResult
        getGroupCollection(GroupsInputType git) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetGroupInfoResponse.GetGroupInfoResult
        getGroupInfo(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void addGroup(String string, String string1, PrincipalType pt,
        String string2, String string3) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void addGroupToRole(String string, String string1) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void updateGroupInfo(String string, String string1,
        String string2, PrincipalType pt, String string3) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void removeGroup(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void removeGroupFromRole(String string, String string1) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetRoleCollectionFromWebResponse.GetRoleCollectionFromWebResult
        getRoleCollectionFromWeb() {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetRoleCollectionFromGroupResponse.GetRoleCollectionFromGroupResult
        getRoleCollectionFromGroup(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetRoleCollectionFromUserResponse.GetRoleCollectionFromUserResult
        getRoleCollectionFromUser(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetRoleCollectionResponse.GetRoleCollectionResult 
        getRoleCollection(RolesInputType rit) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public RoleOutputType getRoleInfo(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void addRole(String string, String string1, int i) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void addRoleDef(String string, String string1, BigInteger bi) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void updateRoleInfo(String string, String string1,
        String string2, int i) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void updateRoleDefInfo(String string, String string1, 
        String string2, BigInteger bi) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public void removeRole(String string) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetUserLoginFromEmailResponse.GetUserLoginFromEmailResult 
        getUserLoginFromEmail(EmailsInputType eit) {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetRolesAndPermissionsForCurrentUserResponse
        .GetRolesAndPermissionsForCurrentUserResult 
        getRolesAndPermissionsForCurrentUser() {
      throw new UnsupportedOperationException(); 
    }

    @Override
    public GetRolesAndPermissionsForSiteResponse
        .GetRolesAndPermissionsForSiteResult 
        getRolesAndPermissionsForSite() {
      throw new UnsupportedOperationException(); 
    }    
  }

  /**
   * Throw UnsupportedOperationException for all calls.
   */
  private static class UnsupportedSiteData extends DelegatingSiteData {
    @Override
    protected SiteDataSoap delegate() {
      throw new UnsupportedOperationException();
    }
  }

  private static class UnsupportedCallable<V> implements Callable<V> {
    @Override
    public V call() {
      throw new UnsupportedOperationException();
    }
  }

  private static class MockSiteDataFactory implements SiteDataFactory {
    private final String expectedEndpoint;
    private final SiteDataSoap siteData;
    private final MockSiteDataFactory chain;

    private MockSiteDataFactory(String expectedEndpoint, SiteDataSoap siteData,
        MockSiteDataFactory chain) {
      this.expectedEndpoint = expectedEndpoint;
      this.siteData = siteData;
      this.chain = chain;
    }

    public static MockSiteDataFactory blank() {
      return new MockSiteDataFactory(null, null, null);
    }

    public MockSiteDataFactory endpoint(String expectedEndpoint,
        SiteDataSoap siteData) {
      return new MockSiteDataFactory(expectedEndpoint, siteData, this);
    }

    @Override
    public SiteDataSoap newSiteData(String endpoint) {
      if (chain == null) {
        fail("Could not find endpoint " + endpoint);
      }
      if (expectedEndpoint.equals(endpoint)) {
        return siteData;
      }
      return chain.newSiteData(endpoint);
    }
  }

  private static class ReferenceSiteData extends DelegatingSiteData {
    private volatile SiteDataSoap siteData = new UnsupportedSiteData();

    @Override
    protected SiteDataSoap delegate() {
      return siteData;
    }

    public void setSiteDataSoap(SiteDataSoap siteData) {
      if (siteData == null) {
        throw new NullPointerException();
      }
      this.siteData = siteData;
    }
  }

  private static class MockSiteData extends UnsupportedSiteData {
    private final List<URLSegmentsExchange> urlSegmentsList;
    private final List<ContentExchange> contentList;
    private final List<ChangesExchange> changesList;
    private final List<SiteAndWebExchange> siteAndWebList;

    private MockSiteData() {
      this.urlSegmentsList = Collections.emptyList();
      this.contentList = Collections.emptyList();
      this.changesList = Collections.emptyList();
      this.siteAndWebList = Collections.emptyList();
    }

    private MockSiteData(List<URLSegmentsExchange> urlSegmentsList,
        List<ContentExchange> contentList, List<ChangesExchange> changesList,
        List<SiteAndWebExchange> siteAndWebList) {
      this.urlSegmentsList = urlSegmentsList;
      this.contentList = contentList;
      this.changesList = changesList;
      this.siteAndWebList = siteAndWebList;
    }

    @Override
    public void getURLSegments(String strURL,
        Holder<Boolean> getURLSegmentsResult, Holder<String> strWebID,
        Holder<String> strBucketID, Holder<String> strListID,
        Holder<String> strItemID) {
      for (URLSegmentsExchange ex : urlSegmentsList) {
        if (!ex.strURL.equals(strURL)) {
          continue;
        }
        setValue(getURLSegmentsResult, ex.getURLSegmentsResult);
        setValue(strWebID, ex.strWebID);
        setValue(strBucketID, ex.strBucketID);
        setValue(strListID, ex.strListID);
        setValue(strItemID, ex.strItemID);
        return;
      }
      fail("Could not find " + strURL);
    }

    @Override
    public void getContent(ObjectType objectType, String objectId,
        String folderUrl, String itemId, boolean retrieveChildItems,
        boolean securityOnly, Holder<String> lastItemIdOnPage,
        Holder<String> getContentResult) {
      for (ContentExchange ex : contentList) {
        if (!ex.objectType.equals(objectType)
            || !Objects.equal(ex.objectId, objectId)
            || !Objects.equal(ex.folderUrl, folderUrl)
            || !Objects.equal(ex.itemId, itemId)
            || ex.retrieveChildItems != retrieveChildItems
            || ex.securityOnly != securityOnly) {
          continue;
        }
        setValue(lastItemIdOnPage, ex.lastItemIdOnPage);
        setValue(getContentResult, ex.getContentResult);
        return;
      }
      fail("Could not find " + objectType + ", " + objectId + ", " + folderUrl
          + ", " + itemId + ", " + retrieveChildItems + ", " + securityOnly);
    }

    @Override
    public void getChanges(ObjectType objectType, String contentDatabaseId,
        Holder<String> lastChangeId, Holder<String> currentChangeId,
        Integer timeout, Holder<String> getChangesResult,
        Holder<Boolean> moreChanges) {
      for (ChangesExchange ex : changesList) {
        if (!ex.objectType.equals(objectType)
            || !Objects.equal(ex.contentDatabaseId, contentDatabaseId)
            || !Objects.equal(ex.lastChangeIdIn, lastChangeId.value)
            || !Objects.equal(ex.currentChangeIdIn, currentChangeId.value)
            || !Objects.equal(ex.timeout, timeout)) {
          continue;
        }
        setValue(lastChangeId, ex.lastChangeIdOut);
        setValue(currentChangeId, ex.currentChangeIdOut);
        setValue(getChangesResult, ex.getChangesResult);
        setValue(moreChanges, ex.moreChanges);
        return;
      }
      fail("Could not find " + objectType + ", " + contentDatabaseId + ", "
          + lastChangeId.value + ", " + currentChangeId.value + ", " + timeout);
    }

    @Override
    public void getSiteAndWeb(String strUrl, Holder<Long> getSiteAndWebResult,
        Holder<String> strSite, Holder<String> strWeb) {
      for (SiteAndWebExchange ex : siteAndWebList) {
        if (!ex.strUrl.equals(strUrl)) {
          continue;
        }
        setValue(getSiteAndWebResult, ex.getSiteAndWebResult);
        setValue(strSite, ex.strSite);
        setValue(strWeb, ex.strWeb);
        return;
      }
      fail("Could not find " + strUrl);
    }

    public static MockSiteData blank() {
      return new MockSiteData();
    }

    public MockSiteData register(URLSegmentsExchange use) {
      return new MockSiteData(addToList(urlSegmentsList, use),
          contentList, changesList, siteAndWebList);
    }

    public MockSiteData register(ContentExchange ce) {
      return new MockSiteData(urlSegmentsList, addToList(contentList, ce),
          changesList, siteAndWebList);
    }

    public MockSiteData register(ChangesExchange ce) {
      return new MockSiteData(urlSegmentsList, contentList,
          addToList(changesList, ce), siteAndWebList);
    }

    public MockSiteData register(SiteAndWebExchange sawe) {
      return new MockSiteData(urlSegmentsList, contentList, changesList,
          addToList(siteAndWebList, sawe));
    }

    /** Creates a new list that has the item appended. */
    private <T> List<T> addToList(List<T> existingList, T item) {
      List<T> l = new ArrayList<T>(existingList);
      l.add(item);
      return Collections.unmodifiableList(l);
    }
  }

  private static class URLSegmentsExchange {
    public final String strURL;
    public final boolean getURLSegmentsResult;
    public final String strWebID;
    public final String strBucketID;
    public final String strListID;
    public final String strItemID;

    public URLSegmentsExchange(String strURL, boolean getURLSegmentsResult,
        String strWebID, String strBucketID, String strListID,
        String strItemID) {
      this.strURL = strURL;
      this.getURLSegmentsResult = getURLSegmentsResult;
      this.strWebID = strWebID;
      this.strBucketID = strBucketID;
      this.strListID = strListID;
      this.strItemID = strItemID;
    }
  }

  private static class ContentExchange {
    public final ObjectType objectType;
    public final String objectId;
    public final String folderUrl;
    public final String itemId;
    public final boolean retrieveChildItems;
    public final boolean securityOnly;
    public final String lastItemIdOnPage;
    public final String getContentResult;

    public ContentExchange(ObjectType objectType, String objectId,
        String folderUrl, String itemId, boolean retrieveChildItems,
        boolean securityOnly, String lastItemIdOnPage,
        String getContentResult) {
      this.objectType = objectType;
      this.objectId = objectId;
      this.folderUrl = folderUrl;
      this.itemId = itemId;
      this.retrieveChildItems = retrieveChildItems;
      this.securityOnly = securityOnly;
      this.lastItemIdOnPage = lastItemIdOnPage;
      this.getContentResult = getContentResult;
    }

    public ContentExchange replaceInContent(String match, String replacement) {
      return new ContentExchange(objectType, objectId, folderUrl, itemId,
          retrieveChildItems, securityOnly, lastItemIdOnPage,
          getContentResult.replace(match, replacement));
    }
  }

  private static class ChangesExchange {
    public final ObjectType objectType;
    public final String contentDatabaseId;
    public final String lastChangeIdIn;
    public final String lastChangeIdOut;
    public final String currentChangeIdIn;
    public final String currentChangeIdOut;
    public final Integer timeout;
    public final String getChangesResult;
    public final boolean moreChanges;

    public ChangesExchange(ObjectType objectType, String contentDatabaseId,
        String lastChangeIdIn, String lastChangeIdOut, String currentChangeIdIn,
        String currentChangeIdOut, Integer timeout, String getChangesResult,
        boolean moreChanges) {
      this.objectType = objectType;
      this.contentDatabaseId = contentDatabaseId;
      this.lastChangeIdIn = lastChangeIdIn;
      this.lastChangeIdOut = lastChangeIdOut;
      this.currentChangeIdIn = currentChangeIdIn;
      this.currentChangeIdOut = currentChangeIdOut;
      this.timeout = timeout;
      this.getChangesResult = getChangesResult;
      this.moreChanges = moreChanges;
    }
  }

  private static class SiteAndWebExchange {
    public final String strUrl;
    public final long getSiteAndWebResult;
    public final String strSite;
    public final String strWeb;

    public SiteAndWebExchange(String strUrl, long getSiteAndWebResult,
        String strSite, String strWeb) {
      this.strUrl = strUrl;
      this.getSiteAndWebResult = getSiteAndWebResult;
      this.strSite = strSite;
      this.strWeb = strWeb;
    }
  }
}
