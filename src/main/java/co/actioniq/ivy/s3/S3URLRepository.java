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

import org.apache.ivy.plugins.repository.url.URLRepository;
import org.apache.ivy.util.url.URLHandler;
import org.apache.ivy.util.url.URLHandlerDispatcher;
import org.apache.ivy.util.url.URLHandlerRegistry;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

class S3URLRepository extends URLRepository {
  private final S3URLHandler s3URLHandler;

  S3URLRepository(String profile) {
    Log.print("new S3URLRepository with profile " + profile);
    s3URLHandler = new S3URLHandler(profile);
    initDispatcher();
    initStreamHandler(profile);
  }

  private void initDispatcher() {
    URLHandler defaultHandler = URLHandlerRegistry.getDefault();
    URLHandlerDispatcher dispatcher;
    if (defaultHandler instanceof URLHandlerDispatcher) {
      Log.print("Using the existing Ivy URLHandlerDispatcher to handle s3:// URLs");
      dispatcher = (URLHandlerDispatcher)defaultHandler;
    } else {
      Log.print("Creating a new Ivy URLHandlerDispatcher to handle s3:// URLs");
      dispatcher = new URLHandlerDispatcher();
      dispatcher.setDefault(defaultHandler);
      URLHandlerRegistry.setDefault(dispatcher);
    }
    dispatcher.setDownloader("s3", s3URLHandler);
  }

  private void initStreamHandler(String profile) {
    // We need s3:// URLs to work without throwing a java.net.MalformedURLException
    // which means installing a dummy URLStreamHandler.  We only install the handler
    // if it's not already installed (since a second call to URL.setURLStreamHandlerFactory
    // will fail).
    try {
      new URL("s3://example.com");
      Log.print("The s3:// URLStreamHandler is already installed");
    } catch (MalformedURLException e) {
      // This means we haven't installed the handler, so install it
      Log.print("Installing the s3:// URLStreamHandler via java.net.URL.setURLStreamHandlerFactory");
      URL.setURLStreamHandlerFactory(new S3URLStreamHandlerFactory(profile));
    }
  }

  public List list(String parent) throws IOException {
    if (parent.startsWith("s3")) {
      return s3URLHandler.list(new URL(parent)).stream().map(URL::toExternalForm).collect(Collectors.toList());
    } else {
      return super.list(parent);
    }
  }
}
