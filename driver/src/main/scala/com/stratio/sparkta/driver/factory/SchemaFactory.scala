/**
 * Copyright (C) 2016 Stratio (http://stratio.com)
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

package com.stratio.sparkta.driver.factory

import org.apache.spark.sql.types._

import com.stratio.sparkta.aggregator.Cube
import com.stratio.sparkta.sdk.TypeOp.TypeOp
import com.stratio.sparkta.sdk.WriteOp.WriteOp
import com.stratio.sparkta.sdk._

object SchemaFactory {

  final val Default_Precision = 10
  final val Default_Scale = 0

  val mapTypes = Map(
    TypeOp.Long -> LongType,
    TypeOp.Double -> DoubleType,
    TypeOp.BigDecimal -> DecimalType(Default_Precision, Default_Scale),
    TypeOp.Int -> IntegerType,
    TypeOp.Boolean -> BooleanType,
    TypeOp.Date -> DateType,
    TypeOp.DateTime -> TimestampType,
    TypeOp.Timestamp -> TimestampType,
    TypeOp.ArrayDouble -> ArrayType(DoubleType),
    TypeOp.ArrayString -> ArrayType(StringType),
    TypeOp.String -> StringType,
    TypeOp.MapStringLong -> MapType(StringType, LongType)
  )

  def cubesOperatorsSchemas(cubes: Seq[Cube],
                            configOptions: Seq[(String, Map[String, String])]): Seq[TableSchema] = {
    val dimensionsSorted = cubes
      .map(cube => cube.expiringDataConfig match {
        case Some(expiringDataConfig) =>
          (cube.dimensions.sorted, cube.operators, Option(expiringDataConfig.timeDimension), cube.name)
        case None =>
          (cube.dimensions.sorted, cube.operators, None, cube.name)
      })

    configOptions.flatMap { case (outputName, configOptions) => {
      for {
        (dimensionCombinations, operators, timeDimension, cubeName) <- getCombinationsWithOperators(configOptions,
          dimensionsSorted)
        extraFields = (getOperatorsFields(operators) ++
          getFixedFieldAggregation(configOptions)).sortWith(_.name < _.name)
        structFields = getDimensionsFields(dimensionCombinations) ++
          timeDimensionFieldType(timeDimension) ++
          extraFields
        schema = StructType(structFields)
      } yield TableSchema(outputName, cubeName, schema, timeDimension)
    }
    }.distinct
  }

  def operatorsKeyOperation(operators: Seq[Operator]): Map[String, (WriteOp, TypeOp)] =
    operators.map(operator => (operator.key ->(operator.writeOperation, operator.returnType))).toMap

  // XXX Private methods.

  private def rowTypeFromOption(optionType: TypeOp): DataType = mapTypes.get(optionType).getOrElse(BinaryType)

  private def getCombinationsWithOperators(configOptions: Map[String, String],
                                           dimensionsSorted:
                                           Seq[(Seq[Dimension], Seq[Operator], Option[String], String)])
  : Seq[(Seq[Dimension], Seq[Operator], Option[String], String)] =
    dimensionsSorted
      .map {
        case (compSorted, operators, timeDimension, cubeName) => (compSorted, operators, timeDimension, cubeName)
    }.distinct

  def timeDimensionFieldType(timeDimension: Option[String]): Seq[StructField] = {
    timeDimension match {
      case None => Seq()
      case Some(timeDimensionValue) => Seq(Output.getFieldType(TypeOp.DateTime, timeDimensionValue, false))
    }

  }

  private def getOperatorsFields(operators: Seq[Operator]): Seq[StructField] =
    operators.map(operator => StructField(operator.key, rowTypeFromOption(operator.returnType), true))

  private def getDimensionsFields(fields: Seq[Dimension]): Seq[StructField] =
    fields.map(field => StructField(field.name, rowTypeFromOption(field.precision.typeOp), false))

  private def getFixedFieldAggregation(options: Map[String, String]): Seq[StructField] =
    options.get(Output.FixedMeasure) match {
      case Some(field) => if (!field.isEmpty && field != "") Seq(Output.defaultStringField(field, true)) else Seq()
      case None => Seq()
    }
}
