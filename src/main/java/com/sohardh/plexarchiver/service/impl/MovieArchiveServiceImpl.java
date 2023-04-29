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

import static org.apache.logging.log4j.util.Strings.isEmpty;

import com.sohardh.plexarchiver.dto.Movie;
import com.sohardh.plexarchiver.dto.Movie.MovieBuilder;
import com.sohardh.plexarchiver.service.MovieArchiveService;
import com.sohardh.plexarchiver.service.PlexFetchDataService;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Slf4j
@Service
public class MovieArchiveServiceImpl implements MovieArchiveService {

  private final PlexFetchDataService plexFetchDataService;
  @Value("${movies.path}")
  private String source;

  @Value("${archive.path}")
  private String sink;

  public MovieArchiveServiceImpl(PlexFetchDataService plexFetchDataService) {
    this.plexFetchDataService = plexFetchDataService;
  }
  /*scp -i /root/.ssh/siteA-rsync-key  dead.letter root@10.10.0.6:/home/hardy*/

  @Override
  public void archiveMovies() {

    Optional<String> moviesWatchedMoreThanOneYearAgo = plexFetchDataService.getMoviesWatchedMoreThanOneYearAgo();
    if (moviesWatchedMoreThanOneYearAgo.isEmpty()) {
      log.info("No candidates to archive found. Skipping archive process.");
      return;
    }
    List<Movie> candidates = new ArrayList<>();
    try {
      candidates = parsePlexResponse(moviesWatchedMoreThanOneYearAgo.get());
    } catch (Exception e) {
      log.error("Error while parsing plex response.", e);
    }
  }

  public List<Movie> parsePlexResponse(String moviesWatchedMoreThanOneYearAgo)
      throws ParserConfigurationException, IOException, SAXException {
    final List<Movie> movies = new ArrayList<>();
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(
        new InputSource(new StringReader(moviesWatchedMoreThanOneYearAgo)));

    NodeList childNodes = doc.getDocumentElement().getChildNodes();
    for (int i = 0; i < childNodes.getLength(); i++) {
      Node videoNode = childNodes.item(i);
      if (videoNode == null || !videoNode.getNodeName().equals("Video")) {
        continue;
      }

      NamedNodeMap attributes = videoNode.getAttributes();
      MovieBuilder movieBuilder = Movie.builder().title(getTextContent(attributes, "title"))
          .thumb(getTextContent(attributes, "thumb")).guid(getTextContent(attributes, "guid"))
          .viewCount(getTextContent(attributes, "viewCount"))
          .addedAt(getTextContent(attributes, "addedAt"))
          .originallyAvailableAt(getTextContent(attributes, "originallyAvailableAt"))
          .lastViewedAt(getTextContent(attributes, "lastViewedAt"));

      NodeList fileNodes = videoNode.getChildNodes();
      if (fileNodes.getLength() == 0) {
        log.warn("No file for movie {} found! Skipping it.", movieBuilder.build().getTitle());
        continue;
      }
      List<String> files = new ArrayList<>();
      for (int j = 0; j < fileNodes.getLength(); j++) {

        Node media = fileNodes.item(j);
        if (media == null || !media.getNodeName().equals("Media")) {
          continue;
        }

        NodeList parts = media.getChildNodes();

        for (int k = 0; k < parts.getLength(); k++) {
          Node part = parts.item(k);
          if (part == null || !part.getNodeName().equals("Part")) {
            continue;
          }
          NamedNodeMap partAttributes = part.getAttributes();
          files.add(getTextContent(partAttributes, "file"));
        }
      }
      movieBuilder.files(files.stream().filter((file -> !isEmpty(file))).toList());
      movies.add(movieBuilder.build());
    }
    return movies;
  }

  private static String getTextContent(NamedNodeMap attributes, String key) {
    Node namedItem = attributes.getNamedItem(key);
    if (namedItem == null) {
      return null;
    }
    return namedItem.getNodeValue();
  }
}
