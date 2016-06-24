/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pyspark_cassandra

trait FromUnreadRow[T] extends (UnreadRow => T) with Serializable

// TODO consider replacying array of Map[String, Object] with a real tuple
// not just here by the way, but all over the place ... this is Scala!
trait ToKV[KV] extends FromUnreadRow[Array[Any]] {
  def apply(row: UnreadRow): Array[Any] = {
    val k = transform(row, row.metaData.columnNames.intersect(row.table.primaryKey.map { _.columnName }))
    val v = transform(row, row.metaData.columnNames.intersect(row.table.regularColumns.map { _.columnName }))
    Array(k, v)
  }

  def transform(row: UnreadRow, columns: IndexedSeq[String]): KV
}

// TODO why ship field names for every row?
case class Row(fields: Seq[String], values: Seq[AnyRef])

object ToRow extends FromUnreadRow[Row] {
  override def apply(row: UnreadRow): Row = {
    Row(
      row.metaData.columnNames,
      row.metaData.columnNames.map {
        c => row.deserialize(c)
      })
  }
}

object ToKVRows extends ToKV[Row] {
  def transform(row: UnreadRow, columns: IndexedSeq[String]): Row = {
    Row(columns, columns.map { c => row.deserialize(c) })
  }
}

object ToTuple extends FromUnreadRow[Array[Any]] {
  def apply(row: UnreadRow): Array[Any] = {
    (row.metaData.columnNames.indices map { c => row.deserialize(c) }).toArray
  }
}

object ToKVTuple extends ToKV[Array[Any]] {
  def transform(row: UnreadRow, columns: IndexedSeq[String]): Array[Any] = {
    columns.map { c => row.deserialize(c) }.toArray
  }
}

object ToDict extends FromUnreadRow[Map[String, Object]] {
  def apply(row: UnreadRow): Map[String, Object] = {
    Map(row.metaData.columnNames.zipWithIndex.map { case (c, i) => c -> row.deserialize(i) }: _*)
  }
}

object ToKVDicts extends ToKV[Map[String, Object]] {
  def transform(row: UnreadRow, columns: IndexedSeq[String]): Map[String, Object] = {
    Map(columns.map { c => c -> row.deserialize(c) }: _*)
  }
}

class JoinedRowTransformer extends (((Any, UnreadRow)) => (Any, Any)) with Serializable {
  def apply(pair: (Any, UnreadRow)): (Any, Any) = {
    val parsed = Format.parser(pair._1).apply(pair._2)
    (pair._1, parsed)
  }
}
