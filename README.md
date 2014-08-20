Pinch
=====

Download individual files from inside ZIP files stored on web servers.

Based on an idea by Edward Patel, the Objective-C implementation is here: [http://github.com/epatel/pinch-objc].

Usage
-----

Pinch works in several steps:

__1.__  Create the Pinch object using the URL to the ZIP you want to pinch.

__2.__  Retrieve the central directory (i.e. the list of all files inside the ZIP archive).

__3.__  From this list, select which files you want to download.

Simple example
------
```java
public void downloadZipContents(String zipUrl, String targetDir) throws MalformedURLException  {
    URL url = new URL(zipUrl);
    Pinch pincher = new Pinch(url);

    // get contents of ZIP archive.
    List<ExtendedZipEntry> list = pincher.parseCentralDirectory();

    // download each file separatly (typical external Android external storage path).
    for (ExtendedZipEntry entry : list) {
        try {
            pincher.downloadFile(entry, targetDir);
        } catch (IOException e) {
            e.printStackTrace();
            break;
        }
    }
}
```

License
-------

    Copyright 2013 Carl Benson
    Copyright 2014 Mattias Niiranen
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
    http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
