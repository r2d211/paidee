package paidee.restaurant.repository

import doobie.free.connection.ConnectionIO
import doobie.implicits._
import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.Logger
import doobie.util.update.Update
import paidee.restaurant.models.{Item, ItemDeleteResponse, ItemName, ResponseBase, TableId, TableResponse}
import paidee.restaurant.repository

sealed trait RestaurantRepository {

  def getItemsInTable(tableId : TableId) : IO[TableResponse]
  def deleteOrder(itemName : Seq[ItemName],tableId : TableId) : IO[ItemDeleteResponse]
  def addItemsToTable(itemName: Seq[ItemName],tableId: TableId) : IO[TableResponse]
}

 class RestaurantRepositoryImpl extends RestaurantRepository{
  private val random =  scala.util.Random
   def r:Int = 1 + random.nextInt(14)
   val logger = Logger("RestaurantRepository")
   val makeOrderReq : (Seq[String], Int) => List[RestaurantOrder] = (seq,t) => seq.map(x=>RestaurantOrder(x,t,r)).toList
   val makeDeleteReq : (Seq[String], Int) => List[DeleteOrder] = (seq,t) => seq.map(x=>DeleteOrder(x,t)).toList
  override def addItemsToTable(items: Seq[ItemName], tableId: TableId): IO[TableResponse] = {
    insertItems(makeOrderReq(items,tableId)).transact(repository.db).attempt.map{
      case Left(e) => logger.error("Unable to add items to table",e.getCause)
        ResponseBase.withError(Some("Sorry, our system has encountered some technical issues"))
      case Right(x) if(x==items.length)=> TableResponse(items = items.iterator.map(x=> Item(x,r)).toSeq,success = true)
      case Right(_) => ResponseBase.withError(Some("Sorry, your order could not be processed"))
    }
  }

  override def deleteOrder(items : Seq[ItemName], tableId: TableId) : IO[ItemDeleteResponse] = {
    deleteOrder(makeDeleteReq(items,tableId)).transact(repository.db).attempt.map{
      case Left(e) => logger.error(s"Unable to delete items for table $tableId",e.getCause)
        ItemDeleteResponse(success = false,Some("Sorry, our system has encountered some technical issues"))
      case Right(x) if(x==items.length) => ItemDeleteResponse(success = true)
      case Right(_) => ItemDeleteResponse(false,Some("Sorry, your order could not be processed"))
    }
  }

  override def getItemsInTable(tableId: TableId):  IO[TableResponse] = {
    queryItems(tableId).transact(repository.db).attempt.map{
        case Left(e) => logger.error(s"Unable to query items for table : $tableId",e.getCause)
          ResponseBase.withError(Some("Sorry, your order could not be processed"))
        case Right(x) => TableResponse(items = x, success = true)
      }
  }



  private def insertItems(order: List[RestaurantOrder]) : ConnectionIO[Int] = {
   val  sql = "insert into myproduct.orders (itemName, tableId, timeToPrepare) values(?, ? ,?) "
    Update[RestaurantOrder](sql).updateMany(order)
  }

  private def deleteOrder(order : List[DeleteOrder]) : ConnectionIO[Int] = {
    val  sql = "delete from myproduct.orders where itemName = ? and tableId = ? "
    Update[DeleteOrder](sql).updateMany(order)
  }

  private def queryItems(tableId : Int) : ConnectionIO[List[Item]] = {
    sql"select itemName, timeToPrepare from myproduct.orders where tableId = $tableId".query[Item].stream.compile.toList
  }
}

case class RestaurantOrder(itemName : ItemName, tableId : Int, timeToPrepare : Int)

case class DeleteOrder(itemName: ItemName, tableId: Int)

object RestaurantRepository {
  def apply(): RestaurantRepository = new RestaurantRepositoryImpl()
}

