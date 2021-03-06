// Copyright 2000-2018 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.flex.uiDesigner;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.assertions.Assertions;
import org.flyti.roboflest.Roboflest;
import org.flyti.roboflest.Roboflest.Assert;

import java.awt.*;
import java.io.File;
import java.io.IOException;

import static com.intellij.flex.uiDesigner.MatcherAssert.assertThat;
import static com.intellij.flex.uiDesigner.TestSocketInputHandler.MessageHandler;
import static org.hamcrest.Matchers.equalTo;

@Flex(version="4.5")
public class UITest extends MxmlTestBase {
  private static final int UI_TEST_CLASS_ID = 5;

  private static Roboflest roboflest;

  private static Reader reader;

  @Override
  protected String getSourceBasePath() {
    return getName().equals("testStyleNavigationToSkinClass") ? "css" : super.getSourceBasePath();
  }

  @Override
  protected void modifySdk(Sdk sdk, SdkModificator sdkModificator) {
    super.modifySdk(sdk, sdkModificator);

    sdkModificator.addRoot(LocalFileSystem.getInstance().findFileByPath(flexSdkRootPath + "/src"), OrderRootType.SOURCES);
  }

  private void init(XmlFile xmlFile) throws IOException, AWTException {
    client.renderDocument(myModule, xmlFile);

    if (roboflest == null) {
      roboflest = new Roboflest();
      reader = socketInputHandler.getReader();
      client.test(myModule, Tests.GET_STAGE_OFFSET);
      socketInputHandler.process(new TestSocketInputHandler.CustomMessageHandler() {
        @Override
        public void process() throws IOException {
          roboflest.setStageOffset(reader);
        }
      });
    }
    else {
      client.flush();
    }
  }

  private void assertClient() throws IOException {
    assertClient(getTestName(false));
  }

  private void assertClient(String methodName) throws IOException {
    client.test(myModule, methodName, UI_TEST_CLASS_ID);
    socketInputHandler.process();
  }

  public void _testStyleNavigationToExternal() throws Exception {
    testFile(new MyTester("styleNavigation", new UIMessageHandler(ServerMethod.RESOLVE_EXTERNAL_INLINE_STYLE_DECLARATION_SOURCE) {
        @Override
        public void process() throws IOException {
          assertThat(client.getModule(reader.readUnsignedShort()), equalTo(myModule));

          XmlAttribute attribute = (XmlAttribute)new ResolveExternalInlineStyleSourceAction(reader, myModule).find();
          Assertions.assertThat(attribute.getDisplayValue()).isEqualTo("spark.skins.spark.ButtonBarLastButtonSkin");
          org.assertj.core.api.Assertions.assertThat(attribute.getTextOffset()).isEqualTo(2186);
        }
      }) {
      }, SPARK_COMPONENTS_FILE);
  }

  public void _testStyleNavigationToSkinClass() throws Exception {
    testFile(new MyTester("styleNavigation", new UIMessageHandler(ServerMethod.OPEN_FILE) {
        @Override
        public void process() throws IOException {
          assertMyProject();
          Assertions.assertThat(reader.readUTF()).isEqualTo(file.getUrl());
          org.assertj.core.api.Assertions.assertThat(reader.readInt()).isEqualTo(96);
        }
      }) {
      }, "ComponentWithCustomSkin.mxml", "CustomSkin.mxml");
  }

  private void assertMyProject() throws IOException {
    assertThat(client.getProject(socketInputHandler.getReader().readUnsignedShort()), equalTo(myProject));
  }

  public void testCloseDocument() throws Exception {
    testFile(new MyTester("closeDocument", new UIMessageHandler(ServerMethod.UNREGISTER_DOCUMENT_FACTORIES) {
      @Override
      public void process() throws IOException {
        org.assertj.core.api.Assertions.assertThat(reader.readIntArray()).containsExactly(DocumentFactoryManager.getInstance().getId(file));
        assertNotAvailable();
        assertClient();
      }
    }) {
    }, "Embed.mxml");
  }

  protected static void assertNotAvailable() throws IOException {
    try {
      Thread.sleep(50); // wait data
    }
    catch (InterruptedException e) {
      fail(e.getMessage());
    }

    org.assertj.core.api.Assertions.assertThat(reader.available()).isEqualTo(0);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  private void interact(final Assert... asserts) throws Exception {
    interact(getTestName(true), asserts);
  }

  private static void interact(String scriptName, final Assert... asserts) throws Exception {
    roboflest.test(new File(DesignerTests.getTestDataPath() + "/roboflest/" + scriptName + ".txt"), asserts);
  }

  private abstract class MyTester implements Tester {
    private final String scriptName;
    private final UIMessageHandler messageHandler;

    MyTester(String scriptName, UIMessageHandler messageHandler) {
      this.scriptName = scriptName;
      this.messageHandler = messageHandler;
    }

    @Override
    public final void test(VirtualFile file, XmlFile xmlFile, VirtualFile originalFile) throws Exception {
      init(xmlFile);
      test(file);
      assertNotAvailable();
    }

    private void test(final VirtualFile file) throws Exception {
      interact(scriptName, new Assert() {
        @Override
        public void test() throws Exception {
          messageHandler.file = file;
          socketInputHandler.process(messageHandler);
        }
      });
    }
  }

  private abstract static class UIMessageHandler implements MessageHandler {
    protected VirtualFile file;
    public final int command;

    UIMessageHandler(int command) {
      this.command = command;
    }

    @Override
    public final int getExpectedCommand() {
      return command;
    }
  }
}
