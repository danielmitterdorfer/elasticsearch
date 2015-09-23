/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy

/**
 * The rest-api-spec tests are loaded from the classpath. However, they
 * currently must be available on the local filesystem. This class encapsulates
 * setting up tasks to copy the rest spec api to test resources.
 */
class RestSpecHack {
    /**
     * Sets up the task and configuration to copy the rest spec.
     * @param project The project to add the copy task to
     * @param includePackagedTests true if the packaged tests should be copied, false otherwise
     */
    static Task setup(Project project, boolean includePackagedTests) {
        project.configurations {
            restSpec
        }
        project.dependencies {
            restSpec "org.elasticsearch:rest-api-spec:${ElasticsearchProperties.version}"
        }
        Map copyRestSpecProps = [
            name: 'copyRestSpec',
            type: Copy,
            dependsOn: [project.configurations.restSpec.buildDependencies, 'processTestResources']
        ]
        Task copyRestSpec = project.tasks.create(copyRestSpecProps) {
            from project.zipTree {
                project.configurations.restSpec.fileCollection {true}.singleFile
            }
            include 'rest-api-spec/api/**'
            if (includePackagedTests) {
                include 'rest-api-spec/test/**'
            }
            into project.sourceSets.test.output.resourcesDir
        }
        project.idea {
            module {
                scopes.TEST.plus.add(project.configurations.restSpec)
            }
        }
        return copyRestSpec
    }
}
