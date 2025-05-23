/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.gate.config.InsightConfiguration
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class LoadBalancerService {
  private static final String GROUP = "loadBalancers"

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  @Autowired
  InsightConfiguration insightConfiguration

  @Autowired
  ObjectMapper objectMapper

  List getAll(String provider = "aws", String selectorKey) {
    Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getLoadBalancers(provider))
  }

  Map get(String name, String selectorKey, String provider = "aws") {
    Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getLoadBalancer(provider, name))
  }

  List getDetailsForAccountAndRegion(String account, String region, String name, String selectorKey, String provider = "aws") {
    try {
      def service = clouddriverServiceSelector.select()
      def accountDetails = objectMapper.convertValue(Retrofit2SyncCall.execute(service.getAccount(account)), Map)
      def loadBalancerDetails = Retrofit2SyncCall.execute(service.getLoadBalancerDetails(provider, account, region, name))

      loadBalancerDetails = loadBalancerDetails.collect { loadBalancerDetail ->
        def loadBalancerContext = loadBalancerDetail.collectEntries {
          return it.value instanceof String ? [it.key, it.value] : [it.key, ""]
        } as Map<String, String>

        def context = [ "account": account, "region": region ] + loadBalancerContext + accountDetails
        def foo = loadBalancerDetail + [
          "insightActions": insightConfiguration.loadBalancer.findResults { it.applyContext(context) }
        ]
        return foo
      }
      return loadBalancerDetails
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        return []
      }
      throw e
    }
  }

  List getClusterLoadBalancers(String appName, String account, String provider, String clusterName, String selectorKey) {
    Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getClusterLoadBalancers(appName, account, clusterName, provider))
  }

  List getApplicationLoadBalancers(String appName, String selectorKey) {
    Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getApplicationLoadBalancers(appName))
  }
}
