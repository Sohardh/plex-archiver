/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.sohardh.plexarchiver.service;

import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class PlexFetchDataService {

  private static final String VIEW_COUNT_FIELD = "viewCount";
  private static final String LAST_VIEWED_AT = "lastViewedAt";
  @Value("${plex.hostname}")
  private String plexHostName;
  @Value("${plex.port}")
  private String plexPort;
  @Value("${plex.token}")
  private String plexToken;

  public Optional<String> getMoviesWatchedMoreThanOneYearAgo() {

    Calendar instance = Calendar.getInstance();
    instance.setTime(new Date());
    instance.add(Calendar.YEAR, -1);

    String urlBuilder = MessageFormat.format(
        "http://{0}:{1}library/sections/2/all?X-Plex-Token={2}&{3}>=1&{4}<={5}",
        plexHostName, plexPort, plexToken, VIEW_COUNT_FIELD, LAST_VIEWED_AT,
        instance.getTimeInMillis());
    WebClient webClient = WebClient.builder()
        .baseUrl(urlBuilder)
        .build();
    try {
      return webClient.get().retrieve().bodyToMono(String.class)
          .blockOptional();
    } catch (Exception e) {
      log.error("An Error occurred while fetch data from plex.", e);
      return Optional.empty();
    }
  }
}
