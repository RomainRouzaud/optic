package com.opticdev.core.sdk

import com.opticdev.core.sdk.descriptions.{Lens, Schema}
import play.api.libs.json._

object SdkDescription {

  private implicit val schemaMapReads = new Reads[Vector[Schema]] {
    override def reads(json: JsValue): JsResult[Vector[Schema]] = {
      val asObject = json.as[JsObject]
      val schemasJson = asObject.value.map(_._2)
      val asSchemas = schemasJson.map(Schema.fromJson)
      JsSuccess(asSchemas.toVector)
    }
  }
  private implicit val lensReads = Lens.lensReads

  private implicit val descriptionReads: Reads[SdkDescription] = Json.reads[SdkDescription]

  def fromJson(jsValue: JsValue): SdkDescription = {
    val description: JsResult[SdkDescription] = Json.fromJson[SdkDescription](jsValue)
    if (description.isSuccess) {
      description.get
    } else {
      throw new Error("Description Parsing Failed "+description)
    }
  }
}

case class SdkDescription(schemas: Vector[Schema], lenses: Vector[Lens])