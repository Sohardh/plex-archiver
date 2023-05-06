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
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
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

  private static final long COPY_WAIT_TIME = 15 * 60 * 1000;
  private final PlexFetchDataService plexFetchDataService;
  private final MovieRepository movieRepository;
  private final MovieFileRepository movieFileRepository;
  @Value("${archive.path}")
  private String archiveFilePath;
  @Value("${archive.host.name}")
  private String archiveHostName;
  @Value("${archive.host.user}")
  private String archiveHostUser;
  @Value("${ssh.file.path}")
  private String sshKeyFilePath;

  public MovieArchiveServiceImpl(PlexFetchDataService plexFetchDataService,
      MovieRepository movieRepository, MovieFileRepository movieFileRepository) {
    this.plexFetchDataService = plexFetchDataService;
    this.movieRepository = movieRepository;
    this.movieFileRepository = movieFileRepository;
  }
  /*scp -i /root/.ssh/siteA-rsync-key  dead.letter root@10.10.0.6:/home/hardy
   * scp -i {ssh key} {sourceFilePath} {hostUser}:{hostname}:{destinationFilePath}
   * */

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
        if (movieFileModel.isEmpty()) {
          movieRepository.deleteById(movie.getGuid());
          return;
        }
        movieFileModelSet.add(movieFileModel.get());
      });
    });
    movieFileRepository.saveAll(movieFileModelSet);
    deleteOriginalFiles(movieFileModelSet);
  }

  private void deleteOriginalFiles(Set<MovieFileModel> movieFileModelSet) {
    movieFileModelSet.forEach(movieFileModel -> {
      var isFileDeleted = false;
      try {
        File file = new File(movieFileModel.getOriginalFile());
        isFileDeleted = file.delete();
        /*TODO */
      } catch (Exception e) {
        log.error("An exception occurred while deleting the file.", e);
      }
      if (!isFileDeleted) {
        movieRepository.deleteById(movieFileModel.getMovieModel().getGuid());
      }
    });
  }

  private Optional<MovieFileModel> creteBackupAndGetMovieFile(MovieModel movie, String movieFile) {
    var movieFileModel = new MovieFileModel();
    movieFileModel.setMovieModel(movie);
    movieFileModel.setOriginalFile(movieFile);

    var destinationFilePath = copyFileToArchive(movieFile);
    if (destinationFilePath.isEmpty()) {
      return Optional.empty();
    }
    movieFileModel.setBackupFile(destinationFilePath.get());
    return Optional.of(movieFileModel);
  }

  private Optional<String> copyFileToArchive(String movieFilePath) {

    String destination = MessageFormat.format("{1}:{2}:{3}", archiveHostUser, archiveHostName,
        archiveFilePath);
    try {

      String[] command = new String[]{"scp", "-i", sshKeyFilePath, movieFilePath, destination};

      ProcessBuilder pb = new ProcessBuilder();
      pb.command(command);
      pb.start();
      pb.wait(COPY_WAIT_TIME);

    } catch (IOException e) {
      log.error(String.format("Something went wrong while copying the movie : %s",
          movieFilePath), e);
      return Optional.empty();
    } catch (InterruptedException e) {
      log.error(String.format("Timeout happened while copying the movie : %s",
          movieFilePath), e);
      return Optional.empty();
    }
    return Optional.of(destination);
  }
}
