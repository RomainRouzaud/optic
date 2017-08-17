package compiler_new.stages

import cognitro.parsers.GraphUtils.{ASTBaseNode, AstPrimitiveNode, AstType, BaseNode}
import cognitro.parsers.ParserBase
import compiler_new.SnippetStageOutput
import compiler_new.errors.{ParserNotFound, SyntaxError, UnexpectedSnippetFormat}
import sdk.descriptions.{Lens, Snippet}
import sourceparsers.SourceParserManager

import scalax.collection.edge.LkDiEdge
import scalax.collection.mutable.Graph

class SnippetBuilder(snippet: Snippet)(implicit lens: Lens) {

  def run : SnippetStageOutput = {
    val parser = getParser()
    val (ast, root) = buildAstTree()

    //calculate enterOn and children
    val (enterOn, children, matchType) = enterOnAndMatchType(ast, root, parser)

    SnippetStageOutput(ast, root, lens.snippet, enterOn, children, matchType)
  }


  def getParser(): ParserBase = {
    val langOption = SourceParserManager.parserByLanguageName(snippet.lang)

    if (langOption.isDefined && langOption.get.supportedVersions.contains(snippet.version)) {
      langOption.get
    } else {
      throw ParserNotFound(snippet.lang, snippet.version)
    }
  }

  def buildAstTree(): (Graph[BaseNode, LkDiEdge], AstPrimitiveNode) = {
    try {
      SourceParserManager.parseString(snippet.block, snippet.lang, Option(snippet.version))
    } catch {
      case a: Throwable => throw SyntaxError(a)
    }
  }

  def enterOnAndMatchType(implicit graph: Graph[BaseNode, LkDiEdge], rootNode: AstPrimitiveNode, parser: ParserBase): (Set[AstType], Vector[AstPrimitiveNode], MatchType.Value) = {
    val programNodeType = parser.programNodeType
    val blockNodeTypes      = parser.blockNodeTypes
    if (programNodeType != rootNode.nodeType) throw new UnexpectedSnippetFormat(programNodeType+" did not the appear first in the AST Tree.")

    val children = rootNode.getChildren.map(_._2)

    children.length match {
      case l if l <= 0  => throw new UnexpectedSnippetFormat("Snippet is empty.")
      case 1            => (Set(children.head.nodeType), children, MatchType.Parent)
      case l if l > 1   => (blockNodeTypes, children, MatchType.Children)
    }

  }

}

object MatchType extends Enumeration {
  val Parent, Children /*Custom*/ = Value
}