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

package com.sohardh.plexarchiver.dao.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movies", schema = "pa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovieModel {

  @Id
  @Column(name = "guid")
  private String guid;
  @Column(name = "title")
  private String title;
  @Column(name = "view_count")
  private String viewCount;
  @Column(name = "added_at")
  private String addedAt;
  @Column(name = "originally_available_at")
  private String originallyAvailableAt;
  @Column(name = "last_viewed_at")
  private String lastViewedAt;
  @Column(name = "thumb")
  private String thumb;

  @OneToMany(cascade = CascadeType.REMOVE, targetEntity = MovieFileModel.class, mappedBy = "guid")
  private List<MovieFileModel> movieFiles;

}
