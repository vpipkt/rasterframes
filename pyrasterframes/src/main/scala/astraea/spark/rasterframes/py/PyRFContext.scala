/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2017-2018 Astraea, Inc.
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
 */
package astraea.spark.rasterframes.py

import astraea.spark.rasterframes._
import astraea.spark.rasterframes.util.CRSParser

import com.vividsolutions.jts.geom.Geometry

import geotrellis.raster.{ArrayTile, CellType, Tile, MultibandTile}
import geotrellis.spark.{SpatialKey, SpaceTimeKey, TileLayerMetadata, MultibandTileLayerRDD, ContextRDD}
import geotrellis.spark.io._

import org.locationtech.geomesa.spark.jts.util.WKBUtils

import org.apache.spark.rdd.RDD
import org.apache.spark.sql._

import spray.json._


/**
 * py4j access wrapper to RasterFrame entry points.
 *
 * @since 11/6/17
 */
class PyRFContext(implicit sparkSession: SparkSession) extends RasterFunctions
  with org.locationtech.geomesa.spark.jts.DataFrameFunctions.Library {

  sparkSession.withRasterFrames

  def toSpatialMultibandTileLayerRDD(rf: RasterFrame): MultibandTileLayerRDD[SpatialKey] =
    rf.toMultibandTileLayerRDD match {
      case Left(spatial) => spatial
      case Right(other) => throw new Exception(s"Expected a MultibandTileLayerRDD[SpatailKey] but got $other instead")
    }

  def toSpaceTimeMultibandTileLayerRDD(rf: RasterFrame): MultibandTileLayerRDD[SpaceTimeKey] =
    rf.toMultibandTileLayerRDD match {
      case Right(temporal) => temporal
      case Left(other) => throw new Exception(s"Expected a MultibandTileLayerRDD[SpaceTimeKey] but got $other instead")
    }

  /**
   * Converts a ContextRDD[Spatialkey, MultibandTile, TileLayerMedadata[Spatialkey]] to a RasterFrame
   */
  def asRF(
    layer: ContextRDD[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]],
    bandCount: java.lang.Integer
  ): RasterFrame = {
    implicit val pr = PairRDDConverter.forSpatialMultiband(bandCount.toInt)
    layer.toRF
  }

  /**
   * Converts a ContextRDD[SpaceTimeKey, MultibandTile, TileLayerMedadata[SpaceTimeKey]] to a RasterFrame
   */
  def asRF(
    layer: ContextRDD[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]],
    bandCount: java.lang.Integer
  )(implicit d: DummyImplicit): RasterFrame = {
    implicit val pr = PairRDDConverter.forSpaceTimeMultiband(bandCount.toInt)
    layer.toRF
  }

  /**
    * Base conversion to RasterFrame
    */
  def asRF(df: DataFrame): RasterFrame = {
    df.asRF
  }

  /**
    * Conversion to RasterFrame with spatial key column and TileLayerMetadata specified.
    */
  def asRF(df: DataFrame, spatialKey: Column, tlm: String): RasterFrame = {
    val jtlm = tlm.parseJson.convertTo[TileLayerMetadata[SpatialKey]]
    df.asRF(spatialKey, jtlm)
  }

  /**
    * Convenience functions for use in Python
    */
  def cellType(name: String): CellType = CellType.fromName(name)

  def cellTypes: Seq[String] = astraea.spark.rasterframes.functions.cellTypes()

  /** DESERIALIZATION **/

  def generateTile(cellType: String, cols: Int, rows: Int, bytes: Array[Byte]): ArrayTile = {
    ArrayTile.fromBytes(bytes, this.cellType(cellType), cols, rows)
  }

  def generateGeometry(obj: Array[Byte]): Geometry =  WKBUtils.read(obj)

  def tileColumns(df: DataFrame): Array[Column] =
    df.asRF.tileColumns.toArray

  def spatialKeyColumn(df: DataFrame): Column =
    df.asRF.spatialKeyColumn

  def temporalKeyColumn(df: DataFrame): Column =
    df.asRF.temporalKeyColumn.orNull

  def tileToIntArray(col: Column): Column = tileToArray[Int](col)

  def tileToDoubleArray(col: Column): Column = tileToArray[Double](col)

  // return toRaster, get just the tile, and make an array out of it
  def toIntRaster(df: DataFrame, colname: String, cols: Int, rows: Int): Array[Int] = {
    df.asRF.toRaster(df.col(colname), cols, rows).toArray()
  }

  def toDoubleRaster(df: DataFrame, colname: String, cols: Int, rows: Int): Array[Double] = {
    df.asRF.toRaster(df.col(colname), cols, rows).toArrayDouble()
  }

  def tileLayerMetadata(df: DataFrame): String =
    // The `fold` is required because an `Either` is retured, depending on the key type.
    df.asRF.tileLayerMetadata.fold(_.toJson, _.toJson).prettyPrint

  def spatialJoin(df: DataFrame, right: DataFrame): RasterFrame = df.asRF.spatialJoin(right.asRF)

  def withBounds(df: DataFrame): RasterFrame = df.asRF.withBounds()

  def withCenter(df: DataFrame): RasterFrame = df.asRF.withCenter()

  def reprojectGeometry(geometryCol: Column, srcName: String, dstName: String): Column = {
    val src = CRSParser(srcName)
    val dst = CRSParser(dstName)
    reprojectGeometry(geometryCol, src, dst)
  }
}
