/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

import java.io.IOException;

/**
 * Converts the given context into a JSON string.
 *
 * TODO rz - Add recursive template rendering:
 * Given an implementation "{{ json this myVar }}" where "myVar" is "{{ myOtherVar }}", we
 * need to recursively render the variables. In the current implementation, the final JSON
 * output will be '"{{ myOtherVar }}"'. This may be better suited at the TODO in RenderUtil.
 */
public class JsonHelper implements Helper<Object> {

  private ObjectMapper objectMapper;

  public JsonHelper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Object apply(Object context, Options options) throws IOException {
    return new String(objectMapper.writeValueAsBytes(context));
  }
}
