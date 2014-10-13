package com.twitter.algebird

import org.scalatest.{ PropSpec, Matchers, WordSpec }
import org.scalatest.prop.PropertyChecks
import org.scalacheck.{ Gen, Arbitrary }

import CMSHasherImplicits._ // required, although e.g. IntelliJ IDEA may flag it as unused import

class CmsLaws extends PropSpec with PropertyChecks with Matchers {
  import BaseProperties._

  val DELTA = 1E-8
  val EPS = 0.005
  val SEED = 1

  private def createArbitrary[K: Numeric](cmsMonoid: CMSMonoid[K]): Arbitrary[CMS[K]] = {
    val k = implicitly[Numeric[K]]
    Arbitrary {
      for (v <- Gen.choose(0, 10000)) yield cmsMonoid.create(k.fromInt(v))
    }
  }

  property("CountMinSketch[Short] is a Monoid") {
    implicit val cmsMonoid = CMS.monoid[Short](EPS, DELTA, SEED)
    implicit val cmsGen = createArbitrary[Short](cmsMonoid)
    monoidLaws[CMS[Short]]
  }

  property("CountMinSketch[Int] is a Monoid") {
    implicit val cmsMonoid = CMS.monoid[Int](EPS, DELTA, SEED)
    implicit val cmsGen = createArbitrary[Int](cmsMonoid)
    monoidLaws[CMS[Int]]
  }

  property("CountMinSketch[Long] is a Monoid") {
    implicit val cmsMonoid = CMS.monoid[Long](EPS, DELTA, SEED)
    implicit val cmsGen = createArbitrary[Long](cmsMonoid)
    monoidLaws[CMS[Long]]
  }

  property("CountMinSketch[BigInt] is a Monoid") {
    implicit val cmsMonoid = CMS.monoid[BigInt](EPS, DELTA, SEED)
    implicit val cmsGen = createArbitrary[BigInt](cmsMonoid)
    monoidLaws[CMS[BigInt]]
  }

}

class TopPctCmsLaws extends PropSpec with PropertyChecks with Matchers {
  import BaseProperties._

  val DELTA = 1E-8
  val EPS = 0.005
  val SEED = 1
  val HEAVY_HITTERS_PCT = 0.1

  private def createArbitrary[K: Numeric](cmsMonoid: TopPctCMSMonoid[K]): Arbitrary[TopPctCMS[K]] = {
    val k = implicitly[Numeric[K]]
    Arbitrary {
      for (v <- Gen.choose(0, 10000)) yield cmsMonoid.create(k.fromInt(v))
    }
  }

  property("TopPctCms[Short] is a Monoid") {
    implicit val cmsMonoid = TopPctCMS.monoid[Short](EPS, DELTA, SEED, HEAVY_HITTERS_PCT)
    implicit val cmsGen = createArbitrary[Short](cmsMonoid)
    monoidLaws[TopPctCMS[Short]]
  }

  property("TopPctCms[Int] is a Monoid") {
    implicit val cmsMonoid = TopPctCMS.monoid[Int](EPS, DELTA, SEED, HEAVY_HITTERS_PCT)
    implicit val cmsGen = createArbitrary[Int](cmsMonoid)
    monoidLaws[TopPctCMS[Int]]
  }

  property("TopPctCms[Long] is a Monoid") {
    implicit val cmsMonoid = TopPctCMS.monoid[Long](EPS, DELTA, SEED, HEAVY_HITTERS_PCT)
    implicit val cmsGen = createArbitrary[Long](cmsMonoid)
    monoidLaws[TopPctCMS[Long]]
  }

  property("TopPctCms[BigInt] is a Monoid") {
    implicit val cmsMonoid = TopPctCMS.monoid[BigInt](EPS, DELTA, SEED, HEAVY_HITTERS_PCT)
    implicit val cmsGen = createArbitrary[BigInt](cmsMonoid)
    monoidLaws[TopPctCMS[BigInt]]
  }

}

class CMSShortTest extends CMSTest[Short]
class CMSIntTest extends CMSTest[Int]
class CMSLongTest extends CMSTest[Long]
class CMSBigIntTest extends CMSTest[BigInt]

abstract class CMSTest[K: Ordering: CMSHasher: Numeric] extends WordSpec with Matchers {

  val DELTA = 1E-10
  val EPS = 0.001
  val SEED = 1

  // We use TopPctCMS for testing CMSCounting functionality.  We argue that because TopPctCMS[K] encapsulates CMS[K]
  // and uses it for all its counting/querying functionality (like an adapter) we can test CMS[K] indirectly through
  // testing TopPctCMS[K].
  val COUNTING_CMS_MONOID = {
    val ANY_HEAVY_HITTERS_PCT = 0.1 // heavy hitters functionality is not relevant for the tests using this monoid
    TopPctCMS.monoid[K](EPS, DELTA, SEED, ANY_HEAVY_HITTERS_PCT)
  }

  val RAND = new scala.util.Random

  implicit class IntCast(x: Int) {
    def toK[A: Numeric]: A = implicitly[Numeric[A]].fromInt(x)
  }

  implicit class SeqCast(xs: Seq[Int]) {
    def toK[A: Numeric]: Seq[A] = xs map { _.toK[A] }
  }

  implicit class SetCast(xs: Set[Int]) {
    def toK[A: Numeric]: Set[A] = xs map { _.toK[A] }
  }

  /**
   * Returns the exact frequency of {x} in {data}.
   */
  def exactFrequency(data: Seq[K], x: K): Long = data.count(_ == x)

  /**
   * Returns the exact inner product between two data streams, when the streams
   * are viewed as count vectors.
   */
  def exactInnerProduct(data1: Seq[K], data2: Seq[K]): Long = {
    val counts1 = data1.groupBy(x => x).mapValues(_.size)
    val counts2 = data2.groupBy(x => x).mapValues(_.size)

    (counts1.keys.toSet & counts2.keys.toSet).map { k => counts1(k) * counts2(k) }.sum
  }

  /**
   * Returns the elements in {data} that appear at least heavyHittersPct * data.size times.
   */
  def exactHeavyHitters(data: Seq[K], heavyHittersPct: Double): Set[K] = {
    val counts = data.groupBy(x => x).mapValues(_.size)
    val totalCount = counts.values.sum
    counts.filter { _._2 >= heavyHittersPct * totalCount }.keys.toSet
  }

  "A Count-Min sketch implementing CMSCounting" should {

    "count total number of elements in a stream" in {
      val totalCount = 1243
      val range = 234
      val data = (0 to (totalCount - 1)).map { _ => RAND.nextInt(range) }.toK[K]
      val cms = COUNTING_CMS_MONOID.create(data)

      cms.totalCount should be (totalCount)
    }

    "estimate frequencies" in {
      val totalCount = 5678
      val range = 897
      val data = (0 to (totalCount - 1)).map { _ => RAND.nextInt(range) }.toK[K]
      val cms = COUNTING_CMS_MONOID.create(data)

      (0 to 100).foreach { _ =>
        val x = RAND.nextInt(range).toK[K]
        val exact = exactFrequency(data, x)
        val approx = cms.frequency(x).estimate
        val estimationError = approx - exact
        val maxError = approx - cms.frequency(x).min
        val beWithinTolerance = be >= 0L and be <= maxError

        approx should be >= exact
        estimationError should beWithinTolerance
      }
    }

    "exactly compute frequencies in a small stream" in {
      val one = COUNTING_CMS_MONOID.create(1.toK[K])
      val two = COUNTING_CMS_MONOID.create(2.toK[K])
      val cms = COUNTING_CMS_MONOID.plus(COUNTING_CMS_MONOID.plus(one, two), two)

      cms.frequency(0.toK[K]).estimate should be (0)
      cms.frequency(1.toK[K]).estimate should be (1)
      cms.frequency(2.toK[K]).estimate should be (2)

      val three = COUNTING_CMS_MONOID.create(Seq(1, 1, 1).toK[K])
      three.frequency(1.toK[K]).estimate should be (3)
      val four = COUNTING_CMS_MONOID.create(Seq(1, 1, 1, 1).toK[K])
      four.frequency(1.toK[K]).estimate should be (4)
      val cms2 = COUNTING_CMS_MONOID.plus(four, three)
      cms2.frequency(1.toK[K]).estimate should be (7)
    }

    "estimate inner products" in {
      val totalCount = 5234
      val range = 1390
      val data1 = (0 to (totalCount - 1)).map { _ => RAND.nextInt(range) }.toK[K]
      val data2 = (0 to (totalCount - 1)).map { _ => RAND.nextInt(range) }.toK[K]
      val cms1 = COUNTING_CMS_MONOID.create(data1)
      val cms2 = COUNTING_CMS_MONOID.create(data1)

      val approxA = cms1.innerProduct(cms2)
      val approx = approxA.estimate
      val exact = exactInnerProduct(data1, data2)
      val estimationError = approx - exact
      val maxError = approx - approxA.min
      val beWithinTolerance = be >= 0L and be <= maxError

      approx should be(cms2.innerProduct(cms1).estimate)
      approx should be >= exact
      estimationError should beWithinTolerance
    }

    "exactly compute inner product of small streams" in {
      // Nothing in common.
      val a1 = List(1, 2, 3).toK[K]
      val a2 = List(4, 5, 6).toK[K]
      COUNTING_CMS_MONOID.create(a1).innerProduct(COUNTING_CMS_MONOID.create(a2)).estimate should be (0)

      // One element in common.
      val b1 = List(1, 2, 3).toK[K]
      val b2 = List(3, 5, 6).toK[K]
      COUNTING_CMS_MONOID.create(b1).innerProduct(COUNTING_CMS_MONOID.create(b2)).estimate should be (1)

      // Multiple, non-repeating elements in common.
      val c1 = List(1, 2, 3).toK[K]
      val c2 = List(3, 2, 6).toK[K]
      COUNTING_CMS_MONOID.create(c1).innerProduct(COUNTING_CMS_MONOID.create(c2)).estimate should be (2)

      // Multiple, repeating elements in common.
      val d1 = List(1, 2, 2, 3, 3).toK[K]
      val d2 = List(2, 3, 3, 6).toK[K]
      COUNTING_CMS_MONOID.create(d1).innerProduct(COUNTING_CMS_MONOID.create(d2)).estimate should be (6)
    }

    "work as an Aggregator" in {
      val data1 = Seq(1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 5).toK[K]

      val cms = CMS.aggregator[K](EPS, DELTA, SEED).apply(data1)
      cms.frequency(1.toK[K]).estimate should be(1L)
      cms.frequency(2.toK[K]).estimate should be(2L)
      cms.frequency(3.toK[K]).estimate should be(3L)
      cms.frequency(4.toK[K]).estimate should be(4L)
      cms.frequency(5.toK[K]).estimate should be(5L)

      val topPctCMS = {
        val anyHeavyHittersPct = 0.1 // exact setting not relevant for this test
        TopPctCMS.aggregator[K](EPS, DELTA, SEED, anyHeavyHittersPct).apply(data1)
      }
      topPctCMS.frequency(1.toK[K]).estimate should be(1L)
      topPctCMS.frequency(2.toK[K]).estimate should be(2L)
      topPctCMS.frequency(3.toK[K]).estimate should be(3L)
      topPctCMS.frequency(4.toK[K]).estimate should be(4L)
      topPctCMS.frequency(5.toK[K]).estimate should be(5L)
    }

  }

  "A Count-Min sketch implementing CMSHeavyHitters" should {

    "estimate heavy hitters" in {
      // Simple way of making some elements appear much more often than others.
      val data1 = (1 to 3000).map { _ => RAND.nextInt(3) }.toK[K]
      val data2 = (1 to 3000).map { _ => RAND.nextInt(10) }.toK[K]
      val data3 = (1 to 1450).map { _ => -1 }.toK[K] // element close to being a 20% heavy hitter
      val data = data1 ++ data2 ++ data3

      // Find elements that appear at least 20% of the time.
      val cms = TopPctCMS.monoid[K](EPS, DELTA, SEED, 0.2).create(data)

      val trueHhs = exactHeavyHitters(data, cms.heavyHittersPct)
      val estimatedHhs = cms.heavyHitters

      // All true heavy hitters must be claimed as heavy hitters.
      trueHhs.intersect(estimatedHhs) should be (trueHhs)

      // It should be very unlikely that any element with count less than
      // (heavyHittersPct - eps) * totalCount is claimed as a heavy hitter.
      val minHhCount = (cms.heavyHittersPct - cms.eps) * cms.totalCount
      val infrequent = data.groupBy{ x => x }.mapValues{ _.size }.filter{ _._2 < minHhCount }.keys.toSet
      infrequent.intersect(estimatedHhs) should be ('empty)
    }

    "drop old heavy hitters when new heavy hitters replace them" in {
      val monoid = TopPctCMS.monoid[K](EPS, DELTA, SEED, 0.3)
      val cms1 = monoid.create(Seq(1, 2, 2).toK[K])
      cms1.heavyHitters should be (Set(1, 2))

      val cms2 = cms1 ++ monoid.create(2.toK[K])
      cms2.heavyHitters should be (Set(2))

      val cms3 = cms2 ++ monoid.create(1.toK[K])
      cms3.heavyHitters should be (Set(1, 2))

      val cms4 = cms3 ++ monoid.create(Seq(0, 0, 0, 0, 0, 0).toK[K])
      cms4.heavyHitters should be (Set(0))
    }

    "exactly compute heavy hitters in a small stream" in {
      val data1 = Seq(1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 5).toK[K]
      val cms1 = TopPctCMS.monoid[K](EPS, DELTA, SEED, 0.01).create(data1)
      val cms2 = TopPctCMS.monoid[K](EPS, DELTA, SEED, 0.1).create(data1)
      val cms3 = TopPctCMS.monoid[K](EPS, DELTA, SEED, 0.3).create(data1)
      val cms4 = TopPctCMS.monoid[K](EPS, DELTA, SEED, 0.9).create(data1)
      cms1.heavyHitters should be (Set(1, 2, 3, 4, 5))
      cms2.heavyHitters should be (Set(2, 3, 4, 5))
      cms3.heavyHitters should be (Set(5))
      cms4.heavyHitters should be (Set[K]())
    }

    "work as an Aggregator" in {
      val data1 = Seq(1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 5).toK[K]
      val cms1 = TopPctCMS.aggregator[K](EPS, DELTA, SEED, 0.01).apply(data1)
      val cms2 = TopPctCMS.aggregator[K](EPS, DELTA, SEED, 0.1).apply(data1)
      val cms3 = TopPctCMS.aggregator[K](EPS, DELTA, SEED, 0.3).apply(data1)
      val cms4 = TopPctCMS.aggregator[K](EPS, DELTA, SEED, 0.9).apply(data1)
      cms1.heavyHitters should be (Set(1, 2, 3, 4, 5))
      cms2.heavyHitters should be (Set(2, 3, 4, 5))
      cms3.heavyHitters should be (Set(5))
      cms4.heavyHitters should be (Set[K]())
    }

  }

}