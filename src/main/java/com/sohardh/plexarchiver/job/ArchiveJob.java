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

package com.sohardh.plexarchiver.job;

import com.sohardh.plexarchiver.service.MovieArchiveService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Component
@AllArgsConstructor
@Slf4j
public class ArchiveJob {

  private final MovieArchiveService movieArchiveService;

  @Scheduled(cron = "${schedule.movie.archive.cron.exp}")
  public void scheduleMovieArchive() {
    log.info("Archiving old/unused movies.");
    StopWatch stopWatch = new StopWatch();
    movieArchiveService.archiveMovies();
    stopWatch.stop();
    log.info("Archiving completed in {}s", stopWatch.getTotalTimeSeconds());
  }
}
