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

package com.sohardh.plexarchiver.service.impl;

import static com.sohardh.plexarchiver.util.PlexDataParserUtil.parsePlexResponse;

import com.sohardh.plexarchiver.dao.model.MovieFileModel;
import com.sohardh.plexarchiver.dao.model.MovieModel;
import com.sohardh.plexarchiver.dao.repository.MovieFileRepository;
import com.sohardh.plexarchiver.dao.repository.MovieRepository;
import com.sohardh.plexarchiver.dto.Movie;
import com.sohardh.plexarchiver.service.MovieArchiveService;
import com.sohardh.plexarchiver.service.PlexFetchDataService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MovieArchiveServiceImpl implements MovieArchiveService {

  private final PlexFetchDataService plexFetchDataService;
  private final MovieRepository movieRepository;
  private final MovieFileRepository movieFileRepository;
  @Value("${movies.path}")
  private String source;

  @Value("${archive.path}")
  private String sink;

  public MovieArchiveServiceImpl(PlexFetchDataService plexFetchDataService,
      MovieRepository movieRepository, MovieFileRepository movieFileRepository) {
    this.plexFetchDataService = plexFetchDataService;
    this.movieRepository = movieRepository;
    this.movieFileRepository = movieFileRepository;
  }
  /*scp -i /root/.ssh/siteA-rsync-key  dead.letter root@10.10.0.6:/home/hardy*/

  @Override
  public void archiveMovies() {

    Optional<String> moviesWatchedMoreThanOneYearAgo = plexFetchDataService.getMoviesWatchedMoreThanOneYearAgo();
    if (moviesWatchedMoreThanOneYearAgo.isEmpty()) {
      log.info("No candidates to archive found. Skipping archive process.");
      return;
    }
    List<Movie> candidates;
    try {
      candidates = parsePlexResponse(moviesWatchedMoreThanOneYearAgo.get(), log);

      var guids = candidates.stream().map(Movie::getGuid)
          .collect(Collectors.toSet());

      var newMovies = new HashSet<Movie>();
      movieRepository.findAllById(guids)
          .forEach(movieModel -> {
            if (guids.contains(movieModel.getGuid())) {
              return;
            }
            Optional<Movie> newMovieOptional = candidates.stream()
                .filter(movie -> movie.getGuid().equals(movieModel.getGuid())).findFirst();
            newMovieOptional.ifPresent(newMovies::add);
          });

      var newMovieModels = newMovies.stream().map(movie -> {
        var movieModel = new MovieModel();
        movieModel.setGuid(movie.getGuid());
        movieModel.setThumb(movie.getThumb());
        movieModel.setLastViewedAt(movie.getLastViewedAt());
        movieModel.setTitle(movie.getTitle());
        movieModel.setAddedAt(movie.getAddedAt());
        movieModel.setViewCount(movie.getViewCount());
        movieModel.setOriginallyAvailableAt(movie.getOriginallyAvailableAt());
        return movieModel;
      }).collect(Collectors.toSet());

      movieRepository.saveAll(newMovieModels);

      saveMovieFiles(newMovies, newMovieModels);


    } catch (Exception e) {
      log.error("Error while parsing plex response.", e);
    }
  }

  private void saveMovieFiles(Set<Movie> newMovies, Set<MovieModel> newMovieModels) {
    var movieFileModelSet = new HashSet<MovieFileModel>();

    newMovieModels.forEach(movie -> {

      Optional<Movie> movieOptional = newMovies.stream()
          .filter(newMovie -> movie.getGuid().equals(newMovie.getGuid())).findFirst();
      List<String> files = new ArrayList<>();
      if (movieOptional.isPresent()) {
        files = movieOptional.get().getFiles();
      }
      files.forEach(movieFile -> {
        var movieFileModel = creteBackupAndGetMovieFile(movie, movieFile);
        movieFileModelSet.add(movieFileModel);
      });
    });
    movieFileRepository.saveAll(movieFileModelSet);
  }

  private MovieFileModel creteBackupAndGetMovieFile(MovieModel movie, String movieFile) {
    var movieFileModel = new MovieFileModel();
    movieFileModel.setGuid(movie);
    movieFileModel.setOriginalFile(movieFile);
    /* TODO implement file backup */
    movieFileModel.setBackupFile("save backup file  path ");
    return movieFileModel;
  }
}
