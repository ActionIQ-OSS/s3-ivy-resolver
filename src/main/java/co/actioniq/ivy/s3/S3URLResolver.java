/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package co.actioniq.ivy.s3;

import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.ResolverSettings;

import java.util.List;

public class S3URLResolver extends IBiblioResolver {
  private static final String M2_PER_MODULE_PATTERN = "[revision]/[artifact]-[revision](-[classifier]).[ext]";
  private static final String M2_PATTERN = "[organisation]/[module]/" + M2_PER_MODULE_PATTERN;

  private S3URLRepository s3URLRepository;

  public S3URLResolver() {
    Log.print("S3URLResolver empty constructor");
    setM2compatible(true);
    setPattern(M2_PATTERN);
    s3URLRepository = new S3URLRepository("default");
  }

  public S3URLResolver(String name, String root, String prefix, List<String> patterns) {
    this();
    Log.print("S3URLResolver full constructor");
    setName(name);
    setRoot(root);
    setArtifactPatterns(patterns);
    setIvyPatterns(patterns);
  }

  public String getTypeName() { return "s3"; }

  public void setProfile(String profile) {
    if (profile == null || profile.isEmpty()) {
      profile = "default";
    }
    s3URLRepository = new S3URLRepository(profile);
  }
}
