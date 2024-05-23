/*
 * Copyright 2024 Google LLC
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

package com.google.tsunami.plugins.detectors.exposedui.argocd;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.tsunami.common.data.NetworkEndpointUtils.forHostnameAndPort;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.inject.util.Modules;
import com.google.protobuf.util.Timestamps;
import com.google.tsunami.common.net.http.HttpClientModule;
import com.google.tsunami.common.time.testing.FakeUtcClock;
import com.google.tsunami.common.time.testing.FakeUtcClockModule;
import com.google.tsunami.plugin.payload.testing.FakePayloadGeneratorModule;
import com.google.tsunami.plugin.payload.testing.PayloadTestHelper;
import com.google.tsunami.plugins.detectors.exposedui.argocd.Annotations.OobSleepDuration;
import com.google.tsunami.proto.DetectionReport;
import com.google.tsunami.proto.DetectionReportList;
import com.google.tsunami.proto.DetectionStatus;
import com.google.tsunami.proto.NetworkService;
import com.google.tsunami.proto.Severity;
import com.google.tsunami.proto.TargetInfo;
import com.google.tsunami.proto.Vulnerability;
import com.google.tsunami.proto.VulnerabilityId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import javax.inject.Inject;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;

/** Unit tests for {@link ExposedArgoCDDetector}. */
@RunWith(JUnit4.class)
public final class ExposedArgoCDDetectorTest {
  private final FakeUtcClock fakeUtcClock =
      FakeUtcClock.create().setNow(Instant.parse("2024-12-03T00:00:00.00Z"));

  private final MockWebServer mockTargetService = new MockWebServer();
  private final MockWebServer mockCallbackServer = new MockWebServer();

  @Inject private ExposedArgoCDDetector detector;

  TargetInfo targetInfo;
  NetworkService targetNetworkService;
  private final SecureRandom testSecureRandom =
      new SecureRandom() {
        @Override
        public void nextBytes(byte[] bytes) {
          Arrays.fill(bytes, (byte) 0xFF);
        }
      };

  @Bind(lazy = true)
  @OobSleepDuration
  private int sleepDuration = 1;

  private void createInjector() {
    Guice.createInjector(
            new FakeUtcClockModule(fakeUtcClock),
            new HttpClientModule.Builder().build(),
            FakePayloadGeneratorModule.builder()
                .setCallbackServer(mockCallbackServer)
                .setSecureRng(testSecureRandom)
                .build(),
            Modules.override(new ExposedArgoCDDetectorBootstrapModule())
                .with(BoundFieldModule.of(this)))
        .injectMembers(this);
  }

  @Before
  public void setUp() throws IOException {
    mockCallbackServer.start();
  }

  @After
  public void tearDown() throws Exception {
    mockTargetService.shutdown();
    mockCallbackServer.shutdown();
  }

  @Test
  public void detect_whenVulnerable_returnsVulnerability_Cve202229165() throws IOException {
    startMockWebServer(true);
    createInjector();
    mockCallbackServer.enqueue(PayloadTestHelper.generateMockSuccessfulCallbackResponse());

    DetectionReportList detectionReports =
        detector.detect(targetInfo, ImmutableList.of(targetNetworkService));

    assertThat(detectionReports.getDetectionReportsList())
        .containsExactly(
            DetectionReport.newBuilder()
                .setTargetInfo(targetInfo)
                .setNetworkService(targetNetworkService)
                .setDetectionTimestamp(
                    Timestamps.fromMillis(Instant.now(fakeUtcClock).toEpochMilli()))
                .setDetectionStatus(DetectionStatus.VULNERABILITY_VERIFIED)
                .setVulnerability(
                    Vulnerability.newBuilder()
                        .setMainId(
                            VulnerabilityId.newBuilder()
                                .setPublisher("TSUNAMI_COMMUNITY")
                                .setValue("ARGOCD_INSTANCE_EXPOSED"))
                        .setSeverity(Severity.CRITICAL)
                        .setTitle("Argo-cd instance Exposed")
                        .setDescription(
                            "Argo-cd instance is vulnerable to CVE-2022-29165."
                                + "The authentication can be bypassed"
                                + "All applications can be accessed by public and therefore can"
                                + " be modified. Results in instance being compromised.")
                        .setRecommendation(
                            "Patched versions are 2.1.15, and 2.3.4, and 2.2.9, and"
                                + " 2.1.15. Please update argo-cd to these versions and higher."))
                .build());
    Truth.assertThat(mockTargetService.getRequestCount()).isEqualTo(5);
    Truth.assertThat(mockCallbackServer.getRequestCount()).isEqualTo(1);
  }

  @Test
  public void detect_whenVulnerable_returnsVulnerability_Exposed_Ui() throws IOException {
    startMockWebServer(false);
    createInjector();
    mockCallbackServer.enqueue(PayloadTestHelper.generateMockSuccessfulCallbackResponse());

    DetectionReportList detectionReports =
        detector.detect(targetInfo, ImmutableList.of(targetNetworkService));

    assertThat(detectionReports.getDetectionReportsList())
        .containsExactly(
            DetectionReport.newBuilder()
                .setTargetInfo(targetInfo)
                .setNetworkService(targetNetworkService)
                .setDetectionTimestamp(
                    Timestamps.fromMillis(Instant.now(fakeUtcClock).toEpochMilli()))
                .setDetectionStatus(DetectionStatus.VULNERABILITY_VERIFIED)
                .setVulnerability(
                    Vulnerability.newBuilder()
                        .setMainId(
                            VulnerabilityId.newBuilder()
                                .setPublisher("TSUNAMI_COMMUNITY")
                                .setValue("ARGOCD_INSTANCE_EXPOSED"))
                        .setSeverity(Severity.CRITICAL)
                        .setTitle("Argo-cd instance Exposed")
                        .setDescription(
                            "Argo-cd instance is misconfigured."
                                + "The instance is not authenticated."
                                + "All applications can be accessed by public and therefore can"
                                + " be modified. Results in instance being compromised.")
                        .setRecommendation("Please disable public access to your argo-cd instance"))
                .build());
    Truth.assertThat(mockTargetService.getRequestCount()).isEqualTo(4);
    Truth.assertThat(mockCallbackServer.getRequestCount()).isEqualTo(1);
  }

  @Test
  public void detect_ifNotVulnerable_doesNotReportVuln_Exposed_Ui() throws IOException {
    startMockWebServer(false);
    createInjector();
    mockCallbackServer.enqueue(PayloadTestHelper.generateMockUnsuccessfulCallbackResponse());
    mockCallbackServer.enqueue(PayloadTestHelper.generateMockUnsuccessfulCallbackResponse());
    DetectionReportList detectionReports =
        detector.detect(targetInfo, ImmutableList.of(targetNetworkService));
    assertThat(detectionReports.getDetectionReportsList()).isEmpty();
    Truth.assertThat(mockTargetService.getRequestCount()).isEqualTo(6);
  }

  @Test
  public void detect_ifNotVulnerable_doesNotReportVuln_Cve202229165() throws IOException {
    startMockWebServer(true);
    createInjector();
    mockCallbackServer.enqueue(PayloadTestHelper.generateMockUnsuccessfulCallbackResponse());
    DetectionReportList detectionReports =
        detector.detect(targetInfo, ImmutableList.of(targetNetworkService));
    assertThat(detectionReports.getDetectionReportsList()).isEmpty();
    Truth.assertThat(mockTargetService.getRequestCount()).isEqualTo(4);
  }

  private void startMockWebServer(boolean mustHaveForgedCookie) throws IOException {
    final Dispatcher dispatcher =
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            // if withAnForgedCookie is True then we should check the forged cookie for all requests
            if (mustHaveForgedCookie
                && !Objects.equals(
                    request.getHeaders().get("Cookie"),
                    "argocd.token="
                        + "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiJ9."
                        + "TGGTTHuuGpEU8WgobXxkrBtW3NiR3dgw5LR-1DEW3BQ")) {
              return new MockResponse().setResponseCode(403);
            }
            // get an existing model name
            if (Objects.equals(request.getPath(), "/api/v1/projects?fields=items.metadata.name")
                && request.getMethod().equals("GET")) {
              return new MockResponse()
                  .setBody("{\"items\":[{\"metadata\":{\"name\":\"default\"}}]}")
                  .setResponseCode(200);
            }
            // Attempting to unload model
            if (Objects.equals(request.getPath(), "/api/v1/clusters")
                && request.getMethod().equals("GET")) {
              return new MockResponse()
                  .setBody(
                      "{\"metadata\": {},\"items\": [{\"server\": "
                          + "\"https://kubernetes.default.svc\",\"name\": \"in-cluster\","
                          + "\"config\": {\"tlsClientConfig\": {\"insecure\": false}}}]}")
                  .setResponseCode(200);
            }
            // Creating model repo layout: uploading the model
            // Or Creating model repo layout: uploading model config
            if (Objects.equals(request.getPath(), "/api/v1/applications")) {
              if (request.getMethod().equals("POST")
                  && !request.getBody().readString(StandardCharsets.UTF_8).isEmpty()
                  && Objects.requireNonNull(request.getHeaders().get("Content-Type"))
                      .equals("application/json")
                  && (Objects.equals(request.getBody().readString(StandardCharsets.UTF_8), "s")
                      || request.getBody().readString(StandardCharsets.UTF_8).startsWith("s"))) {
                return new MockResponse().setResponseCode(200);
              }
            }
            // Loading model to trigger payload
            if (Objects.equals(
                request.getPath(),
                "/api/v1/applications/tsunami-security-scanner?cascade=true&"
                    + "propagationPolicy=foreground&appNamespace=argocd")) {
              if (request.getMethod().equals("DELETE")
                  && request.getBody().readString(StandardCharsets.UTF_8).isEmpty()) {
                return new MockResponse().setResponseCode(200);
              }
            }
            return new MockResponse().setBody("[{}]").setResponseCode(200);
          }
        };
    mockTargetService.setDispatcher(dispatcher);
    mockTargetService.start();
    mockTargetService.url("/");

    targetNetworkService =
        NetworkService.newBuilder()
            .setNetworkEndpoint(
                forHostnameAndPort(mockTargetService.getHostName(), mockTargetService.getPort()))
            .addSupportedHttpMethods("POST")
            .build();
    targetInfo =
        TargetInfo.newBuilder()
            .addNetworkEndpoints(targetNetworkService.getNetworkEndpoint())
            .build();
  }
}
