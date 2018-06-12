package com.opticdev.arrow.changes.evaluation

import better.files.File
import com.opticdev.arrow.changes.location.{EndOfFile, ResolvedChildInsertLocation, ResolvedLocation, ResolvedRawLocation}
import com.opticdev.core.sourcegear.project.monitoring.FileStateMonitor
import com.opticdev.core.utils.StringUtils
import com.opticdev.marvin.common.ast._
import com.opticdev.marvin.common.ast.OpticGraphConverter._
import com.opticdev.marvin.common.helpers.LineOperations
import com.opticdev.marvin.runtime.mutators.MutatorImplicits._
import com.opticdev.marvin.runtime.mutators.NodeMutatorMap
import play.api.libs.json.JsString

import scala.util.{Failure, Success, Try}

object InsertCode {

  def atLocation(generatedNode: (NewAstNode, String), resolvedLocation: ResolvedLocation)(implicit filesStateMonitor : FileStateMonitor): ChangeResult = Try {
    resolvedLocation match {
      case loc : ResolvedRawLocation => {
        val fileContents = filesStateMonitor.contentsForFile(loc.file).getOrElse("")
        val changed = StringUtils.insertAtIndex(fileContents, loc.rawPosition, generatedNode._2)
        FileChanged(loc.file, changed, Some(PatchInfo(Range(loc.rawPosition, loc.rawPosition), generatedNode._2)))
      }

      case loc : EndOfFile => {
        val fileContents = filesStateMonitor.contentsForFile(loc.file).getOrElse("")
        val addition = {
          if (fileContents.isEmpty) {
            generatedNode._2
          } else {
            "\n\n"+ generatedNode._2
          }
        }

        val changed = StringUtils.insertAtIndex(fileContents, fileContents.length, addition)
        FileChanged(loc.file, changed, Some(PatchInfo(Range(fileContents.length, fileContents.length), addition)))
      }

      case loc : ResolvedChildInsertLocation => {

        val fileContents = filesStateMonitor.contentsForFile(loc.file).getOrElse("")

        val marvinAstParent = loc.parent.toMarvinAstNode(loc.graph, fileContents, loc.parser)

        val childrenIndent = marvinAstParent.indent.next

        val gcWithLeadingWhiteSpace = LineOperations.padAllLinesWith(childrenIndent.generate, generatedNode._2)

        implicit val nodeMutatorMap = loc.parser.marvinSourceInterface.asInstanceOf[NodeMutatorMap]

        val blockPropertyPath = loc.parser.blockNodeTypes.getPropertyPath(loc.parent.nodeType).get

        val array = marvinAstParent.properties.getOrElse(blockPropertyPath, AstArray()).asInstanceOf[AstArray]

        val newNode = NewAstNode(
          generatedNode._1.nodeType,
          generatedNode._1.properties,
          //overriding with our own string.
          Some(gcWithLeadingWhiteSpace))

        val newArray = array.children.patch(loc.index, Seq(newNode), 0)

        val newProperties: AstProperties = marvinAstParent.properties + (blockPropertyPath -> AstArray(newArray:_*))

        val changes = marvinAstParent.mutator.applyChanges(marvinAstParent, newProperties)

        val updatedFileContents = StringUtils.replaceRange(fileContents, loc.parent.range, changes)

        //compute patch info
        val diff = updatedFileContents diff fileContents
        val start = updatedFileContents.indexOf(diff)
        val end = {
          val i = fileContents.lastIndexOf(updatedFileContents.substring(start+diff.length))
          if (i == -1) start else i
        }

        FileChanged(loc.file, updatedFileContents, Some(PatchInfo(Range(start, end), diff)))

      }
    }
  } match {
    case Success(fileChanged) => fileChanged
    case Failure(e) => {
      e.printStackTrace()
      FailedToChange(e)
    }
  }

}
