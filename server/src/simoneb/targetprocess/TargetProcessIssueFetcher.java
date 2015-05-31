/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package simoneb.targetprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.issueTracker.AbstractIssueFetcher;
import jetbrains.buildServer.issueTracker.BasicIssueFetcherAuthenticator;
import jetbrains.buildServer.issueTracker.IssueData;
import jetbrains.buildServer.issueTracker.IssueFetcherAuthenticator;
import jetbrains.buildServer.issueTracker.errors.ConnectionException;
import jetbrains.buildServer.issueTracker.errors.IssueTrackerErrorException;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.cache.EhCacheUtil;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Oleg Rybak <oleg.rybak@jetbrains.com>
 */
public class TargetProcessIssueFetcher extends AbstractIssueFetcher {

  private interface Containers {
    String ENTITY_STATE   = "EntityState";
    String ENTITY_TYPE   = "EntityType";
  }

  private interface Fields {
    String STATE_NAME    = "Name";
    String ENTITY_TYPE_NAME    = "Name";
  }

  private static final String URL_TEMPLATE_GET_STORY = "%s/api/%s/UserStories/%s";
  private static final String URL_TEMPLATE_GET_BUG = "%s/api/%s/Bugs/%s";
  private final static Logger LOG = Logger.getInstance(TargetProcessIssueFetcher.class.getName());

  public TargetProcessIssueFetcher(@NotNull final EhCacheUtil cacheUtil) {
    super(cacheUtil);
  }

  /*
   * see doc:
   * http://dev.targetprocess.com/rest/getting_started
   */

  private static final String apiVersion = "v1"; // rest api version

  @NotNull
  public IssueData getIssue(@NotNull final String host, @NotNull final String id, @Nullable final Credentials credentials) throws Exception {
    String rightUrl;

    try {
      rightUrl = String.format(URL_TEMPLATE_GET_BUG, host, apiVersion, id);
      fetchHttpFile(rightUrl, credentials);
    } catch(IssueTrackerErrorException exception) {
      rightUrl = String.format(URL_TEMPLATE_GET_STORY, host, apiVersion, id);
      fetchHttpFile(rightUrl, credentials);
    }

    final String cacheKey = getUrl(host, id);
    final String finalRightUrl = rightUrl;

    return getFromCacheOrFetch(cacheKey, new FetchFunction() {
      @NotNull
      public IssueData fetch() throws Exception {
        InputStream body = fetchHttpFile(finalRightUrl, credentials);
        return doGetIssue(body, finalRightUrl);
      }
    });
  }

  @NotNull
  @Override
  protected InputStream fetchHttpFile(@NotNull String url, @Nullable Credentials credentials) throws IOException {
    return getHttpFile(url, new BasicIssueFetcherAuthenticator(credentials));
  }

  @NotNull
  protected InputStream getHttpFile(@NotNull String url, @NotNull IssueFetcherAuthenticator authenticator) throws IOException {
    LOG.debug("Performing HTTP request: \"" + url + "\"");

    URL parsedUrl = new URL(url);
    HttpClient httpClient = HttpUtil.createHttpClient(120, parsedUrl, authenticator.getCredentials(), authenticator.isBasicAuth());  // 2 minutes
    if (StringUtil.isEmpty(parsedUrl.getProtocol()) || StringUtil.isEmpty(parsedUrl.getHost())) {
      throw new UnknownHostException("Failed to parse the host in URL: " + url);
    }


    GetMethod get = new GetMethod(url);
    authenticator.applyAuthScheme(get);
    get.setRequestHeader("Accept", "application/json");

    int code = httpClient.executeMethod(get);

    if (code < 200 || code >= 300) {
      handleHttpError(code, get);
    }

    LOG.debug("HTTP response: " + code + ", length: " + get.getResponseContentLength());
    InputStream stream = get.getResponseBodyAsStream();
    if (stream == null) {
      throw new ConnectionException("HTTP response is not available from \"" + url + "\"");
    }
    return stream;
  }

  private IssueData doGetIssue(@NotNull final InputStream input, String restUrl) throws Exception {
    final Map map = new ObjectMapper().readValue(input, Map.class);
    return parseIssueData(map, restUrl);
  }

  private IssueData parseIssueData(@NotNull final Map map, String restUrl) {
    final Map entityState = getContainer(map, Containers.ENTITY_STATE);
    final Map entityType = getContainer(map, Containers.ENTITY_TYPE);

    return new IssueData(
            String.valueOf(map.get("Id")),
            CollectionsUtil.asMap(
                    IssueData.SUMMARY_FIELD, String.valueOf(map.get("Name")),
                    IssueData.STATE_FIELD, getField(entityState, Fields.STATE_NAME),
                    IssueData.TYPE_FIELD, getField(entityType, Fields.ENTITY_TYPE_NAME),
                    "href", restUrl
            ),
            false, // todo: state
            false,
            restUrl
    );
  }

  private Map getContainer(final Map map, @NotNull final String name) {
    return (Map) map.get(name);
  }

  private String getField(final Map map, @NotNull final String name) {
    return (String) map.get(name);
  }

  @NotNull
  public String getUrl(@NotNull String host, @NotNull String id) {
    return host + "entity/" + id;
  }
}
