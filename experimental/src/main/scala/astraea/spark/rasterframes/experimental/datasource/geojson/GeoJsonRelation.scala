/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2018 Astraea. Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     [http://www.apache.org/licenses/LICENSE-2.0]
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 *
 */

package astraea.spark.rasterframes.experimental.datasource.geojson

import java.net.URI

import astraea.spark.rasterframes.experimental.datasource.geojson.GeoJsonRelation._
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.geojson.GeoJsonReader
import org.apache.spark.annotation.Experimental
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.jts.JTSTypes
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import org.apache.spark.sql.sources.{BaseRelation, TableScan}
import org.apache.spark.sql.types.{DataTypes, StringType, StructField, StructType}
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.util.{Failure, Try}

/**
 * Basic support for parsing GeoJson into a DataFrame with a geometry/spatial column.
 * Properties as rendered as a `Map[String,String]`.
 *
 * @since 5/2/18
 */
@Experimental
case class GeoJsonRelation(sqlContext: SQLContext, path: String, inferSchema: Boolean) extends BaseRelation with TableScan  {

  private val preLoaded: Option[DataFrame] = if (inferSchema) {
    val spark = sqlContext.sparkSession
    import spark.implicits._

    val base: RDD[((Geometry, Map[String, JsValue]), Long)] = parseGeom().zipWithIndex()

    val geomSide = base.map(p ⇒ (p._2, p._1._1)).toDF(INDEX_KEY, GEOMETRY_COLUMN)
    val propSide = base.map { case ((_, props), index) ⇒
      val withIndex = props + (INDEX_KEY -> JsNumber(index))
      withIndex.toJson.compactPrint
    }

    val inferredProps = spark.read.json(propSide.toDS)
    val joined = geomSide.join(inferredProps, INDEX_KEY).drop(INDEX_KEY)

    Some(joined)
  } else None

  override def schema: StructType = preLoaded.map(_.schema).getOrElse(
    StructType(Seq(
      StructField(GEOMETRY_COLUMN, JTSTypes.GeometryTypeInstance, false),
      StructField(PROPERTIES_COLUMN,DataTypes.createMapType(StringType, StringType, true), true)
    ))
  )

  private def parseGeom(): RDD[(Geometry, Map[String, JsValue])] = {
    val sc = sqlContext.sparkContext
    sc.wholeTextFiles(path).mapPartitions { iter ⇒
      val reader = new GeoJsonReader()
      for {
        (_, text) ← iter
        dom = text.parseJson.convertTo[GeoJsonDom]
        feature ← dom.features
        geom ← Try(reader.read(feature.geometry.compactPrint)).recoverWith {
          case t: Throwable ⇒
            // Fetch a logger directly to make sure it's in execution scope.
            val logger = org.slf4j.LoggerFactory.getLogger(classOf[GeoJsonRelation])
            logger.error("GeoJSON parsing error on: " + feature.geometry.compactPrint, t)
            Failure(t)
        }.toOption
        props = feature.properties
      } yield (geom, props)
    }
  }

  override def buildScan(): RDD[Row] = preLoaded.map(_.rdd).getOrElse {
    parseGeom().map(p ⇒ Row(p._1, p._2.mapValues(_.toString)))
  }
}

object GeoJsonRelation {
  final val GEOMETRY_COLUMN = "geometry"
  final val PROPERTIES_COLUMN = "properties"
  private final val INDEX_KEY = "__INDEX__"

  case class GeoJsonDom(features: Seq[GeoJsonFeature])
  case class GeoJsonFeature(geometry: JsValue, properties: Map[String, JsValue])

  implicit val featureFormat: RootJsonFormat[GeoJsonFeature] =  jsonFormat2(GeoJsonFeature)
  implicit val domFormat: RootJsonFormat[GeoJsonDom] =  jsonFormat1(GeoJsonDom)
}
