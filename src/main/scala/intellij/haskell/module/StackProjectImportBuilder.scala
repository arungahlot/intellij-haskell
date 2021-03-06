/*
 * Copyright 2014-2017 Rik van der Kleij
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package intellij.haskell.module

import java.io.File
import java.util
import javax.swing.Icon

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.projectImport.ProjectImportBuilder
import intellij.haskell.HaskellIcons
import intellij.haskell.stackyaml.StackYamlComponent
import intellij.haskell.util.{HaskellFileUtil, HaskellProjectUtil}

import scala.collection.JavaConverters._

class StackProjectImportBuilder extends ProjectImportBuilder[Unit] {
  override def getName: String = "Haskell Stack"

  override def getList: util.List[Unit] = new util.ArrayList[Unit]()

  override def getIcon: Icon = HaskellIcons.HaskellSmallLogo

  override def setOpenProjectSettingsAfter(on: Boolean): Unit = {}

  override def setList(list: util.List[Unit]): Unit = ()

  override def isMarked(element: Unit): Boolean = true

  override def commit(project: Project, model: ModifiableModuleModel, modulesProvider: ModulesProvider, artifactModel: ModifiableArtifactModel): java.util.List[Module] = {
    val moduleBuilder = HaskellModuleType.getInstance.createModuleBuilder()

    StackYamlComponent.getPackagePaths(project).foreach(packageRelativePaths => {
      packageRelativePaths.foreach(packageRelativePath => {
        val moduleDirectory = getModuleRootDirectory(packageRelativePath)
        HaskellModuleBuilder.createCabalInfo(project, getFileToImport, packageRelativePath) match {
          case Some(cabalInfo) =>
            val moduleName = cabalInfo.packageName
            moduleBuilder.setCabalInfo(cabalInfo)
            moduleBuilder.setName(moduleName)
            moduleBuilder.setModuleFilePath(getModuleImlFilePath(moduleDirectory, moduleName))
            moduleBuilder.commit(project)
            moduleBuilder.addModuleConfigurationUpdater((_: Module, rootModel: ModifiableRootModel) => {
              moduleBuilder.setupRootModel(rootModel)
            })
          case None => ()
        }
      })

      if (!packageRelativePaths.contains(".")) {
        val parentModuleBuilder = new ParentModuleBuilder(project)
        parentModuleBuilder.setModuleFilePath(new File(project.getBasePath, project.getName + "-parent").getAbsolutePath + ".iml")
        parentModuleBuilder.setName("Parent module")
        parentModuleBuilder.commit(project)
        parentModuleBuilder.addModuleConfigurationUpdater((_: Module, rootModel: ModifiableRootModel) => {
          parentModuleBuilder.setupRootModel(rootModel)
        })
      }
    })
    HaskellProjectUtil.getModuleManager(project).map(_.getModules).getOrElse(Array()).toList.asJava
  }

  private def getModuleRootDirectory(packagePath: String): File = {
    if (packagePath == ".") {
      new File(getFileToImport)
    } else {
      new File(getFileToImport, packagePath)
    }
  }


  private def getModuleImlFilePath(moduleDirectory: File, moduleName: String): String = {
    new File(moduleDirectory, moduleName).getAbsolutePath + ".iml"
  }
}

class ParentModuleBuilder(val project: Project) extends ModuleBuilder {
  override def isOpenProjectSettingsAfter = true

  override def canCreateModule = true

  override def setupRootModel(modifiableRootModel: ModifiableRootModel): Unit = {
    modifiableRootModel.addContentEntry(HaskellFileUtil.getUrlByPath(project.getBasePath))
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = HaskellModuleType.getInstance

  override def getPresentableName = "Parent Module"

  override def getGroupName: String = getPresentableName

  override def isTemplateBased = true

  override def getDescription = "Module at root of project so directories at root level are accessible"
}