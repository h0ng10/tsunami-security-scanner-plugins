# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Example Python vulnerability detector."""
from absl import logging

from google3.google.protobuf import timestamp_pb2
from google3.third_party.java_src.tsunami.plugin_server.py import tsunami_plugin
from google3.third_party.java_src.tsunami.proto import detection_pb2
from google3.third_party.java_src.tsunami.proto import plugin_representation_pb2
from google3.third_party.java_src.tsunami.proto import vulnerability_pb2

PluginInfo = plugin_representation_pb2.PluginInfo


class ExamplePyVulnDetector(tsunami_plugin.VulnDetector):
  """Example Python vulnerability detector class."""

  def GetPluginDefinition(self) -> tsunami_plugin.PluginDefinition:
    """The PluginDefinition for your VulnDetector, derived by your PluginInfo.

    PluginInfo tells Tsunami scanning engine basic information about your
    VulnDetector. This function is used for the Tsunami scanning engine know
    about the existence of your VulnDetector, and any service filtering that
    needs to be defined.

    Returns:
      The PluginDefinition for this VulnDetector.
    """
    return tsunami_plugin.PluginDefinition(
        info=PluginInfo(
            type=PluginInfo.VULN_DETECTION,
            name='ExamplePyVulnDetector',
            version='0.1',
            description='This is an example python plugin',
            author='Alice (alice@company.com)'))

  def Detect(
      self,
      target: tsunami_plugin.TargetInfo,
      matched_services: list[tsunami_plugin.NetworkService]
  ) -> tsunami_plugin.DetectionReportList:
    """This is the main entry point of your VulnDetector.

    Both parameters will be populated by the scanner.

    Args:
      target: Contains the general information about the scan target.
      matched_services: Contains all the network services that matches the
        service filtering given by your PluginDefinition. If no filtering fields
        are added, then matched_services parameter contains all exposed network
        services on the scan target.

    Returns:
      A DetectionReportList for all DetectionReports generated by this
      VulnDetector.
    """
    logging.info('ExamplePyVulnDetector starts detecting.')
    return detection_pb2.DetectionReportList(detection_reports=[
        self._BuildDetectionReport(target, service)
        for service in matched_services
        if self.IsServiceVulnerable()
    ])

  def _BuildDetectionReport(
      self, target: tsunami_plugin.TargetInfo,
      vulnerable_service: tsunami_plugin.NetworkService
  ) -> detection_pb2.DetectionReport:
    """This builds the DetectionReport message for a vulnerable network service."""
    return detection_pb2.DetectionReport(
        target_info=target,
        network_service=vulnerable_service,
        detection_timestamp=timestamp_pb2.Timestamp().GetCurrentTime(),
        detection_status=detection_pb2.VULNERABILITY_VERIFIED,
        vulnerability=vulnerability_pb2.Vulnerability(
            main_id=vulnerability_pb2.VulnerabilityId(
                publisher='vulnerability_id_publisher',
                value='VULNERABILITY_ID'),
            severity=vulnerability_pb2.CRITICAL,
            title='Vulnerability Title',
            description='Detailed description of the vulnerability',
            additional_details=[
                vulnerability_pb2.AdditionalDetail(
                    text_data=vulnerability_pb2.TextData(
                        text='Some additional technical details.'))
            ]))

  def IsServiceVulnerable(self) -> bool:
    """Checks whether a given network service is vulnerable.

    Real detection logic implemented here.

    Returns:
      A bool whether the service is vulnerable or not.
    """
    return True
