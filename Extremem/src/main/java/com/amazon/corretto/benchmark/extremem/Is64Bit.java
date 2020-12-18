// Copyright 2016 higherfrequencytrading.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// You may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.amazon.corretto.benchmark.extremem;

class Is64Bit {

  static boolean is64bit0() {
    String systemProp = System.getProperty("com.ibm.vm.bitmode");
    if (systemProp != null) {
      return "64".equals(systemProp);
    }
    systemProp = System.getProperty("sun.arch.data.model");
    if (systemProp != null) {
      return "64".equals(systemProp);
    }
    systemProp = System.getProperty("java.vm.version");
    return systemProp != null && systemProp.contains("_64");
  }
}
