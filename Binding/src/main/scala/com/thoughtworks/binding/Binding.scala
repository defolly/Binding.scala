/*
The MIT License (MIT)

Copyright (c) 2016 Yang Bo & REA Group Ltd.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package com.thoughtworks.binding

import java.util.EventObject

import com.thoughtworks.sde.core.MonadicFactory._
import com.thoughtworks.enableMembersIf
import com.thoughtworks.sde.core.MonadicFactory
import macrocompat.bundle

import scala.annotation.meta.companionMethod
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.language.higherKinds
import scala.collection.GenSeq
import scala.collection.mutable.Buffer
import scala.util.Try
import scalaz.{Monad, MonadPlus}
import scala.language.experimental.macros

/**
  * @groupname typeClasses Type class instance
  * @groupname implicits Implicits Conversions
  * @groupname expressions Binding Expressions
  * @groupdesc expressions AST nodes of binding expressions
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
object Binding extends MonadicFactory.WithTypeClass[Monad, Binding] {

  override val typeClass = BindingInstances

  @enableMembersIf(c => !c.compilerSettings.exists(_.matches("""^-Xplugin:.*scalajs-compiler_[0-9\.\-]*\.jar$""")))
  private[Binding] object Jvm {

    type ConstantsData[+A] = Seq[A]

    @inline
    def toConstantsData[A](seq: Seq[A]) = seq

    def newBuffer[A] = collection.mutable.ArrayBuffer.empty[A]

    def toCacheData[A](seq: Seq[A]) = seq.toVector

    def emptyCacheData[A]: HasCache[A]#Cache = Vector.empty

    trait HasCache[A] {

      private[Binding] type Cache = Vector[A]

      private[Binding] var cacheData: Cache

      @inline
      private[Binding] final def getCache(n: Int): A = cacheData(n)

      @inline
      private[Binding] final def updateCache(n: Int, newelem: A): Unit = {
        cacheData = cacheData.updated(n, newelem)
      }

      @inline
      private[Binding] final def cacheLength: Int = cacheData.length

      @inline
      private[Binding] final def clearCache(): Unit = {
        cacheData = Vector.empty
      }

      private[Binding] final def removeCache(n: Int): A = {
        val result = cacheData(n)
        cacheData = cacheData.patch(n, Nil, 1)
        result
      }

      private[Binding] final def appendCache(elements: TraversableOnce[A]): GenSeq[A] = {
        val seq = elements.toSeq
        cacheData = cacheData ++ seq
        seq
      }

      private[Binding] final def appendCache(elem: A): Unit = {
        cacheData = cacheData :+ elem
      }

      private[Binding] final def prependCache(elem: A): Unit = {
        cacheData = elem +: cacheData
      }

      private[Binding] final def insertCache(n: Int, elems: Traversable[A]): GenSeq[A] = {
        val seq = elems.toSeq
        cacheData = cacheData.patch(n, seq, 0)
        seq
      }

      private[Binding] final def cacheIterator: Iterator[A] = {
        cacheData.iterator
      }

      private[Binding] final def spliceCache(from: Int, mappedNewChildren: Cache, replaced: Int): TraversableOnce[A] = {
        val oldCache = cacheData
        if (from == 0) {
          cacheData = mappedNewChildren ++ oldCache.drop(replaced)
        } else {
          cacheData = oldCache.patch(from, mappedNewChildren, replaced)
        }
        oldCache.view(from, replaced)
      }

      private[Binding] final def indexOfCache[B >: A](a: B): Int = {
        cacheData.indexOf(a)
      }

    }

  }

  @enableMembersIf(c => c.compilerSettings.exists(_.matches("""^-Xplugin:.*scalajs-compiler_[0-9\.\-]*\.jar$""")))
  private[Binding] object Js {

    type ConstantsData[+A] = scalajs.js.Array[_ <: A]

    @inline
    def toConstantsData[A](seq: Seq[A]) = {
      import scalajs.js.JSConverters._
      seq.toJSArray
    }

    @inline
    def newBuffer[A] = new scalajs.js.Array[A]

    @inline
    implicit final class ReduceToSizeOps[A] @inline()(array: scalajs.js.Array[A]) {
      @inline def reduceToSize(newSize: Int) = array.length = newSize
    }

    @inline
    def toCacheData[A](seq: Seq[A]) = {
      import scalajs.js.JSConverters._
      seq.toJSArray
    }

    @inline
    def emptyCacheData[A]: HasCache[A]#Cache = scalajs.js.Array()

    trait HasCache[A] {

      private[Binding] type Cache = scalajs.js.Array[A]

      private[Binding] def cacheData: Cache

      @inline
      private[Binding] final def getCache(n: Int): A = cacheData(n)

      @inline
      private[Binding] final def updateCache(n: Int, newelem: A): Unit = {
        cacheData(n) = newelem
      }

      @inline
      private[Binding] final def cacheLength: Int = cacheData.length

      @inline
      private[Binding] final def clearCache(): Unit = {
        cacheData.length = 0
      }

      @inline
      private[Binding] final def removeCache(n: Int): A = {
        cacheData.remove(n)
      }

      @inline
      private[Binding] final def appendCache(elements: TraversableOnce[A]): GenSeq[A] = {
        val seq = elements.toSeq
        cacheData ++= seq
        seq
      }

      @inline
      private[Binding] final def appendCache(elem: A): Unit = {
        cacheData += elem
      }

      @inline
      private[Binding] final def prependCache(elem: A): Unit = {
        cacheData.unshift(elem)
      }

      @inline
      private[Binding] final def insertCache(n: Int, elems: Traversable[A]): GenSeq[A] = {
        val seq = elems.toSeq
        cacheData.insertAll(n, elems)
        seq
      }

      @inline
      private[Binding] final def cacheIterator: Iterator[A] = {
        cacheData.iterator
      }

      @inline
      private[Binding] final def spliceCache(from: Int, mappedNewChildren: Cache, replaced: Int): TraversableOnce[A] = {
        cacheData.splice(from, replaced, mappedNewChildren: _*)
      }

      @inline
      private[Binding] final def indexOfCache[B >: A](a: B): Int = {
        cacheData.indexOf(a)
      }

    }

  }

  import Js._
  import Jvm._

  private object Publisher {

    private[Publisher] sealed trait State

    case object Idle extends State

    case object CleanForeach extends State

    case object DirtyForeach extends State

  }

  private[binding] final class Publisher[Subscriber >: Null] {

    import Publisher._

    private val subscribers = newBuffer[Subscriber]

    @volatile
    private var state: State = Idle

    @inline
    def nonEmpty = !isEmpty

    @inline
    def isEmpty = subscribers.forall(_ == null)

    def foreach[U](f: Subscriber => U): Unit = {
      state match {
        case Idle =>
          state = CleanForeach
          subscribers.withFilter(_ != null).foreach(f)
          state match {
            case DirtyForeach => {
              @tailrec
              def compact(i: Int, j: Int): Unit = {
                if (i < subscribers.length) {
                  val subscriber = subscribers(i)
                  if (subscriber == null) {
                    compact(i + 1, j)
                  } else {
                    subscribers(j) = subscriber
                    compact(i + 1, j + 1)
                  }
                } else {
                  subscribers.reduceToSize(j)
                }
              }
              compact(0, 0)
              state = Idle
            }
            case CleanForeach =>
              state = Idle
            case Idle =>
              throw new IllegalStateException("Expect CleanForeach or DirtyForeach")
          }
        case CleanForeach | DirtyForeach =>
          subscribers.withFilter(_ != null).foreach(f)
      }
    }

    @inline
    def subscribe(subscriber: Subscriber): Unit = {
      subscribers += subscriber
    }

    @inline
    def unsubscribe(subscriber: Subscriber): Unit = {
      state match {
        case Idle =>
          subscribers -= subscriber
        case CleanForeach =>
          subscribers(subscribers.indexOf(subscriber)) = null
          state = DirtyForeach
        case DirtyForeach =>
          subscribers(subscribers.indexOf(subscriber)) = null
      }
    }

  }

  private[binding] final class ChangedEvent[+Value](source: AnyRef, val newValue: Value) extends EventObject(source) {
    override def toString = raw"""ChangedEvent[source=$source newValue=$newValue]"""

  }

  private[binding] final class PatchedEvent[+Element](source: AnyRef,
                                                      val from: Int,
                                                      val that: GenSeq[Element],
                                                      val replaced: Int)
      extends EventObject(source) {
    override def toString = raw"""PatchedEvent[source=$source from=$from that=$that replaced=$replaced]"""
  }

  private[binding] trait ChangedListener[-Value] {
    private[binding] def changed(event: ChangedEvent[Value]): Unit
  }

  private[binding] trait PatchedListener[-Element] {
    private[binding] def patched(event: PatchedEvent[Element]): Unit
  }

  /**
    * A data binding expression that never changes.
    *
    * @group expressions
    */
  final case class Constant[+A](override val value: A) extends Binding[A] {

    @deprecated(message = "Use [[value]] instead", since = "11.0.0")
    @inline
    override def get = value

    @inline
    override private[binding] def removeChangedListener(listener: ChangedListener[A]): Unit = {
      // Do nothing because this Constant never changes
    }

    @inline
    override private[binding] def addChangedListener(listener: ChangedListener[A]): Unit = {
      // Do nothing because this Constant never changes
    }
  }

  /**
    * @group expressions
    */
  object Var {
    @inline
    def apply[A](initialValue: A) = new Var(initialValue)
  }

  /**
    * Source variable of data binding expression.
    *
    * You can manually change the value:
    *
    * {{{
    * val bindingVar = Var("initial value")
    * bindingVar.value = "changed value"
    * }}}
    *
    * Then, any data binding expressions that depend on this [[Var]] will be changed automatically.
    *
    * @group expressions
    */
  final class Var[A](private var cache: A) extends Binding[A] {

    private val publisher = new Publisher[ChangedListener[A]]

    @inline
    override def value = cache

    @deprecated(message = "Use [[value]] instead", since = "11.0.0")
    @inline
    override def get: A = value

    /**
      * Changes the current value of this [[Var]], and reevaluates any expressions that depends on this [[Var]].
      *
      * @note This method must not be invoked inside a `@dom` method body.
      */
    def value_=(newValue: A): Unit = {
      if (cache != newValue) {
        cache = newValue
        val event = new ChangedEvent(this, newValue)
        for (listener <- publisher) {
          listener.changed(event)
        }
      }
    }

    @deprecated(message = "Use [[value_=]] instead.", since = "11.0.0")
    def :=(newValue: A): Unit = {
      value_=(newValue)
    }

    @inline
    override private[binding] def removeChangedListener(listener: ChangedListener[A]): Unit = {
      publisher.unsubscribe(listener)
    }

    @inline
    override private[binding] def addChangedListener(listener: ChangedListener[A]): Unit = {
      publisher.subscribe(listener)
    }
  }

  private final class Map[A, B](upstream: Binding[A], f: A => B) extends Binding[B] with ChangedListener[A] {

    private val publisher = new Publisher[ChangedListener[B]]

    private var cache: B = _

    private def refreshCache() = {
      cache = f(upstream.value)
    }

    @inline
    override private[binding] def value: B = {
      cache
    }

    @inline
    override private[binding] def addChangedListener(listener: ChangedListener[B]): Unit = {
      if (publisher.isEmpty) {
        upstream.addChangedListener(this)
        refreshCache()
      }
      publisher.subscribe(listener)
    }

    @inline
    override private[binding] def removeChangedListener(listener: ChangedListener[B]): Unit = {
      publisher.unsubscribe(listener)
      if (publisher.isEmpty) {
        upstream.removeChangedListener(this)
      }
    }

    override final def changed(upstreamEvent: ChangedEvent[A]): Unit = {
      val oldCache = cache
      val newCache = f(upstreamEvent.newValue)
      cache = newCache
      if (oldCache != newCache) {
        val event = new ChangedEvent(Map.this, newCache)
        for (listener <- publisher) {
          listener.changed(event)
        }
      }
    }
  }

  private final class FlatMap[A, B](upstream: Binding[A], f: A => Binding[B])
      extends Binding[B]
      with ChangedListener[B] {

    private val publisher = new Publisher[ChangedListener[B]]

    private val forwarder = new ChangedListener[A] {

      override final def changed(upstreamEvent: ChangedEvent[A]): Unit = {
        val oldCache = cache
        oldCache.removeChangedListener(FlatMap.this)
        val newCache = f(upstreamEvent.newValue)
        cache = newCache
        newCache.addChangedListener(FlatMap.this)
        if (oldCache.value != newCache.value) {
          val event = new ChangedEvent(FlatMap.this, newCache.value)
          for (listener <- publisher) {
            listener.changed(event)
          }
        }
      }
    }

    @inline
    override private[binding] def changed(upstreamEvent: ChangedEvent[B]) = {
      val event = new ChangedEvent(FlatMap.this, upstreamEvent.newValue)
      for (listener <- publisher) {
        listener.changed(event)
      }
    }

    @inline
    override private[binding] def addChangedListener(listener: ChangedListener[B]): Unit = {
      if (publisher.isEmpty) {
        upstream.addChangedListener(forwarder)
        refreshCache()
        cache.addChangedListener(this)
      }
      publisher.subscribe(listener)
    }

    private var cache: Binding[B] = _

    private def refreshCache() = {
      cache = f(upstream.value)
    }

    override private[binding] def value: B = {
      @tailrec
      @inline
      def tailrecGetValue(binding: Binding[B]): B = {
        binding match {
          case flatMap: FlatMap[_, B] => tailrecGetValue(flatMap.cache)
          case _                      => binding.value
        }
      }
      tailrecGetValue(cache)
    }

    override private[binding] def removeChangedListener(listener: ChangedListener[B]): Unit = {
      publisher.unsubscribe(listener)
      if (publisher.isEmpty) {
        upstream.removeChangedListener(forwarder)
        cache.removeChangedListener(this)
      }
    }

  }

  /**
    * Monad instances for [[Binding]].
    *
    * @group typeClasses
    */
  implicit object BindingInstances extends Monad[Binding] {

    @inline
    override def map[A, B](fa: Binding[A])(f: A => B): Binding[B] = {
      fa match {
        case Constant(a) =>
          Constant(f(a))
        case _ =>
          new Map[A, B](fa, f)
      }
    }

    @inline
    override def bind[A, B](fa: Binding[A])(f: A => Binding[B]): Binding[B] = {
      fa match {
        case Constant(a) =>
          f(a)
        case _ =>
          new FlatMap[A, B](fa, f)
      }
    }

    @inline
    override def point[A](a: => A): Binding[A] = Constant(a)

    @inline
    override def ifM[B](value: Binding[Boolean], ifTrue: => Binding[B], ifFalse: => Binding[B]): Binding[B] = {
      bind(value)(if (_) ifTrue else ifFalse)
    }

    @inline
    override def whileM[G[_], A](p: Binding[Boolean], body: => Binding[A])(implicit G: MonadPlus[G]): Binding[G[A]] = {
      ifM(p, bind(body)(x => map(whileM(p, body))(xs => G.plus(G.point(x), xs))), point(G.empty))
    }

    @inline
    override def whileM_[A](p: Binding[Boolean], body: => Binding[A]): Binding[Unit] = {
      ifM(p, bind(body)(_ => whileM_(p, body)), point(()))
    }

    @inline
    override def untilM[G[_], A](f: Binding[A], cond: => Binding[Boolean])(implicit G: MonadPlus[G]): Binding[G[A]] = {
      bind(f)(x => map(whileM(map(cond)(!_), f))(xs => G.plus(G.point(x), xs)))
    }

    @inline
    override def untilM_[A](f: Binding[A], cond: => Binding[Boolean]): Binding[Unit] = {
      bind(f)(_ => whileM_(map(cond)(!_), f))
    }

  }

  @bundle
  private[Binding] class Macros(val c: scala.reflect.macros.blackbox.Context) {

    import c.universe._

    final def map(f: Tree): Tree = {
      val apply @ Apply(TypeApply(Select(self, TermName("map")), List(b)), List(f @ Function(vparams, body))) =
        c.macroApplication
      val monadicBody =
        q"""_root_.com.thoughtworks.binding.Binding.apply[$b]($body)"""
      val monadicFunction = atPos(f.pos)(Function(vparams, monadicBody))
      atPos(apply.pos)(q"""$self.mapBinding[$b]($monadicFunction)""")
    }

    final def flatMap(f: Tree): Tree = {
      val apply @ Apply(TypeApply(Select(self, TermName("flatMap")), List(b)), List(f @ Function(vparams, body))) =
        c.macroApplication
      val monadicBody =
        q"""_root_.com.thoughtworks.binding.Binding.apply[_root_.com.thoughtworks.binding.Binding.BindingSeq[$b]]($body)"""
      val monadicFunction = atPos(f.pos)(Function(vparams, monadicBody))
      atPos(apply.pos)(q"""$self.flatMapBinding[$b]($monadicFunction)""")
    }

    final def withFilter(condition: Tree): Tree = {
      val apply @ Apply(Select(self, TermName("withFilter")), List(f @ Function(vparams, body))) = c.macroApplication
      val monadicBody =
        q"""_root_.com.thoughtworks.binding.Binding.apply[_root_.scala.Boolean]($body)"""
      val monadicFunction = atPos(f.pos)(Function(vparams, monadicBody))
      atPos(apply.pos)(q"""$self.withFilterBinding($monadicFunction)""")
    }

    final def bind: Tree = {
      val q"$binding.$methodName" = c.macroApplication
      q"""_root_.com.thoughtworks.sde.core.MonadicFactory.Instructions.each[
        _root_.com.thoughtworks.binding.Binding,
        ${TypeTree(c.macroApplication.tpe)}
      ]($binding)"""
    }

  }

  private[binding] final case class Length(bindingSeq: BindingSeq[_]) extends Binding[Int] with PatchedListener[Any] {

    private val publisher = new Publisher[ChangedListener[Int]]

    @inline
    override private[binding] def value: Int = bindingSeq.value.length

    @inline
    override private[binding] def removeChangedListener(listener: ChangedListener[Int]): Unit = {
      publisher.unsubscribe(listener)
      if (publisher.isEmpty) {
        bindingSeq.removePatchedListener(this)
      }
    }

    @inline
    override private[binding] def addChangedListener(listener: ChangedListener[Int]): Unit = {
      if (publisher.isEmpty) {
        bindingSeq.addPatchedListener(this)
      }
      publisher.subscribe(listener)
    }

    @inline
    override private[binding] def patched(upstreamEvent: PatchedEvent[Any]): Unit = {
      val event = new ChangedEvent[Int](this, bindingSeq.value.length)
      for (subscriber <- publisher) {
        subscriber.changed(event)
      }
    }

  }

  private[binding] case class SingleSeq[+A](element: A) extends collection.immutable.IndexedSeq[A] {

    @inline
    override final def length: Int = 1

    @inline
    override final def apply(idx: Int) = {
      if (idx == 0) {
        element
      } else {
        throw new IndexOutOfBoundsException
      }
    }

    @inline
    override final def iterator = Iterator.single(element)

  }

  private[binding] object Empty extends BindingSeq[Nothing] {
    @inline
    override private[binding] def removePatchedListener(listener: PatchedListener[Nothing]): Unit = {}

    @inline
    override private[binding] def addPatchedListener(listener: PatchedListener[Nothing]): Unit = {}

    @inline
    override private[binding] def value = Nil
  }

  private[Binding] final class ValueProxy[B](underlying: Seq[Binding[B]]) extends Seq[B] {
    @inline
    override def length: Int = {
      underlying.length
    }

    @inline
    override def apply(idx: Int): B = {
      underlying(idx).value
    }

    @inline
    override def iterator: Iterator[B] = {
      underlying.iterator.map(_.value)
    }
  }

  private[binding] final class MapBinding[A, B](upstream: BindingSeq[A], f: A => Binding[B])
      extends BindingSeq[B]
      with HasCache[Binding[B]] {

    private[Binding] var cacheData: Cache = _

    private def refreshCache() = {
      cacheData = (for {
        a <- upstream.value
      } yield f(a))(collection.breakOut)
    }

    override private[binding] def value: Seq[B] with ValueProxy[B] = new ValueProxy(cacheData)

    private val upstreamListener = new PatchedListener[A] {
      override def patched(upstreamEvent: PatchedEvent[A]): Unit = {
        val mappedNewChildren: HasCache[Binding[B]]#Cache = (for {
          child <- upstreamEvent.that
        } yield f(child))(collection.breakOut)
        val oldChildren = spliceCache(upstreamEvent.from, mappedNewChildren, upstreamEvent.replaced)
        for (newChild <- mappedNewChildren) {
          newChild.addChangedListener(childListener)
        }
        for (oldChild <- oldChildren) {
          oldChild.removeChangedListener(childListener)
        }
        val event = new PatchedEvent(MapBinding.this,
                                     upstreamEvent.from,
                                     new ValueProxy(mappedNewChildren),
                                     upstreamEvent.replaced)
        for (listener <- publisher) {
          listener.patched(event)
        }
      }

    }

    private[binding] val publisher = new Publisher[PatchedListener[B]]

    private val childListener = new ChangedListener[B] {

      override def changed(event: ChangedEvent[B]): Unit = {
        val index = indexOfCache(event.getSource)
        for (listener <- publisher) {
          listener.patched(new PatchedEvent(MapBinding.this, index, SingleSeq(event.newValue), 1))
        }
      }
    }

    override private[binding] def removePatchedListener(listener: PatchedListener[B]): Unit = {
      publisher.unsubscribe(listener)
      if (publisher.isEmpty) {
        upstream.removePatchedListener(upstreamListener)
        for (child <- cacheData) {
          child.removeChangedListener(childListener)
        }
      }
    }

    override private[binding] def addPatchedListener(listener: PatchedListener[B]): Unit = {
      if (publisher.isEmpty) {
        upstream.addPatchedListener(upstreamListener)
        refreshCache()
        for (child <- cacheData) {
          child.addChangedListener(childListener)
        }
      }
      publisher.subscribe(listener)
    }

  }

  private[binding] final class FlatProxy[B](underlying: Seq[BindingSeq[B]]) extends Seq[B] {

    @inline
    override def length: Int = {
      underlying.view.map(_.value.length).sum
    }

    @inline
    override def apply(idx: Int): B = {
      val i = underlying.iterator
      @tailrec
      def findIndex(restIndex: Int): B = {
        if (i.hasNext) {
          val subSeq = i.next().value
          val currentLength = subSeq.length
          if (currentLength > restIndex) {
            subSeq(restIndex)
          } else {
            findIndex(restIndex - currentLength)
          }
        } else {
          throw new IndexOutOfBoundsException()
        }
      }
      findIndex(idx)
    }

    @inline
    override def iterator: Iterator[B] = {
      for {
        subSeq <- underlying.iterator
        element <- subSeq.value.iterator
      } yield element
    }
  }

  private[binding] final class FlatMapBinding[A, B](upstream: BindingSeq[A], f: A => BindingSeq[B])
      extends BindingSeq[B]
      with HasCache[BindingSeq[B]] {

    private[Binding] var cacheData: Cache = _

    private def refreshCache() = {
      cacheData = (for {
        a <- upstream.value
      } yield f(a))(collection.breakOut)
    }

    @inline
    override private[binding] def value = new FlatProxy(cacheData)

    @inline
    private def flatIndex(oldCache: Cache, upstreamBegin: Int, upstreamEnd: Int): Int = {
      oldCache.view(upstreamBegin, upstreamEnd).map(_.value.length).sum
    }

    private val upstreamListener = new PatchedListener[A] {
      override private[binding] def patched(upstreamEvent: PatchedEvent[A]): Unit = {
        val mappedNewChildren: Cache = (for {
          child <- upstreamEvent.that
        } yield f(child))(collection.breakOut)
        val flatNewChildren = new FlatProxy(mappedNewChildren)
        val flattenFrom = flatIndex(cacheData, 0, upstreamEvent.from)
        val flattenReplaced = flatIndex(cacheData, upstreamEvent.from, upstreamEvent.from + upstreamEvent.replaced)
        val oldChilden = spliceCache(upstreamEvent.from, mappedNewChildren, upstreamEvent.replaced)
        for (newChild <- mappedNewChildren) {
          newChild.addPatchedListener(childListener)
        }
        for (oldChild <- oldChilden) {
          oldChild.removePatchedListener(childListener)
        }
        if (upstreamEvent.replaced != 0 || flatNewChildren.nonEmpty) {
          val event = new PatchedEvent(FlatMapBinding.this, flattenFrom, flatNewChildren, flattenReplaced)
          for (listener <- publisher) {
            listener.patched(event)
          }
        }
      }

    }

    private[binding] val publisher = new Publisher[PatchedListener[B]]

    private val childListener = new PatchedListener[B] {
      override private[binding] def patched(upstreamEvent: PatchedEvent[B]): Unit = {
        val source = upstreamEvent.getSource.asInstanceOf[BindingSeq[B]]
        val index = flatIndex(cacheData, 0, indexOfCache(source)) + upstreamEvent.from
        val event = new PatchedEvent(FlatMapBinding.this, index, upstreamEvent.that, upstreamEvent.replaced)
        for (listener <- publisher) {
          listener.patched(event)
        }
      }
    }

    @inline
    override private[binding] def removePatchedListener(listener: PatchedListener[B]): Unit = {
      publisher.unsubscribe(listener)
      if (publisher.isEmpty) {
        upstream.removePatchedListener(upstreamListener)
        for (child <- cacheData) {
          child.removePatchedListener(childListener)
        }
      }
    }

    @inline
    override private[binding] def addPatchedListener(listener: PatchedListener[B]): Unit = {
      if (publisher.isEmpty) {
        upstream.addPatchedListener(upstreamListener)
        refreshCache()
        for (child <- cacheData) {
          child.addPatchedListener(childListener)
        }
      }
      publisher.subscribe(listener)
    }
  }

  /**
    * The companion of a data binding expression of a sequence
    *
    * @group expressions
    */
  object BindingSeq {

    @(deprecated @companionMethod)(since = "11.0.0", message = "Use [[BindingSeq.all]] instead")
    implicit final class AsBinding[Element](upstream: BindingSeq[Element])
        extends Binding[Seq[Element]]
        with PatchedListener[Element] {

      private val publisher = new Publisher[ChangedListener[Seq[Element]]]

      @inline
      override private[binding] def value: Seq[Element] = upstream.value

      @inline
      override private[binding] def removeChangedListener(listener: ChangedListener[Seq[Element]]): Unit = {
        publisher.unsubscribe(listener)
        if (publisher.isEmpty) {
          upstream.removePatchedListener(this)
        }
      }

      @inline
      override private[binding] def addChangedListener(listener: ChangedListener[Seq[Element]]): Unit = {
        if (publisher.isEmpty) {
          upstream.addPatchedListener(this)
        }
        publisher.subscribe(listener)
      }

      @inline
      private[binding] def patched(upstreamEvent: PatchedEvent[Element]): Unit = {
        val event = new ChangedEvent[Seq[Element]](AsBinding.this, upstream.value)
        for (listener <- publisher) {
          listener.changed(event)
        }
      }

    }

  }

  /**
    * Data binding expression of a sequence
    *
    * @group expressions
    */
  sealed trait BindingSeq[+A] {

    /** Returns a new [[Binding]] expression of all elements in this [[BindingSeq]]. */
    final def all: Binding[Seq[A]] = new BindingSeq.AsBinding(this)

    /**
      * Enables automatic recalculation.
      *
      * You may invoke this method more than once.
      * Then, when you want to disable automatic recalculation,
      * you must invoke [[unwatch]] same times as the number of calls to this method.
      *
      * @note This method is recursive, which means that the dependencies of this [[BindingSeq]] will be watched as well.
      */
    @inline
    final def watch(): Unit = {
      addPatchedListener(Binding.DummyPatchedListener)
    }

    /**
      * Disables automatic recalculation.
      *
      * @note This method is recursive, which means that the dependencies of this [[BindingSeq]] will be unwatched as well.
      */
    @inline
    final def unwatch(): Unit = {
      removePatchedListener(Binding.DummyPatchedListener)
    }

    private[binding] def value: Seq[A]

    @deprecated(message = "Use [[value]] instead", since = "11.0.0")
    private[binding] def get: Seq[A] = value

    private[binding] def removePatchedListener(listener: PatchedListener[A]): Unit

    private[binding] def addPatchedListener(listener: PatchedListener[A]): Unit

    def length: Binding[Int] = Length(this)

    /**
      * Returns a [[BindingSeq]] that maps each element of this [[BindingSeq]] via `f`
      *
      * @note This method is only available in a `Binding { ??? }` block or a `@dom` method.
      */
    def map[B](f: A => B): BindingSeq[B] = macro Macros.map

    /**
      * Returns a [[BindingSeq]] that flat-maps each element of this [[BindingSeq]] via `f`
      *
      * @note This method is only available in a `Binding { ??? }` block or a `@dom` method.
      */
    def flatMap[B](f: A => BindingSeq[B]): BindingSeq[B] = macro Macros.flatMap

    /**
      * Underlying implementation of [[map]].
      *
      * @note Don't use this method in user code.
      */
    @inline
    final def mapBinding[B](f: A => Binding[B]): BindingSeq[B] = new MapBinding[A, B](this, f)

    /**
      * Underlying implementation of [[flatMap]].
      *
      * @note Don't use this method in user code.
      */
    @inline
    final def flatMapBinding[B](f: A => Binding[BindingSeq[B]]): BindingSeq[B] = {
      new FlatMapBinding[BindingSeq[B], B](new MapBinding[A, BindingSeq[B]](this, f), locally)
    }

    /**
      * Returns a view of this [[BindingSeq]] that applied a filter of `condition`
      */
    def withFilter(condition: A => Boolean): BindingSeq[A]#WithFilter = macro Macros.withFilter

    /**
      * Underlying implementation of [[withFilter]].
      *
      * @note Don't use this method in user code.
      */
    @inline
    final def withFilterBinding(condition: A => Binding[Boolean]): BindingSeq[A]#WithFilter = {
      new WithFilter(condition)
    }

    /**
      * A helper to build complicated comprehension expressions for [[BindingSeq]]
      */
    final class WithFilter(condition: A => Binding[Boolean]) {

      /**
        * Returns a [[BindingSeq]] that maps each element of this [[BindingSeq]] via `f`
        */
      def map[B](f: A => B): BindingSeq[B] = macro Macros.map

      /**
        * Returns a [[BindingSeq]] that flat-maps each element of this [[BindingSeq]] via `f`
        */
      def flatMap[B](f: A => BindingSeq[B]): BindingSeq[B] = macro Macros.flatMap

      /**
        * Returns a view of this [[BindingSeq]] that applied a filter of `condition`
        */
      def withFilter(condition: A => Boolean): WithFilter = macro Macros.withFilter

      /**
        * Underlying implementation of [[withFilter.
        *
        * @note Don't use this method in user code.
        */
      @inline
      def withFilterBinding(nextCondition: A => Binding[Boolean]): WithFilter = {
        new WithFilter({ a =>
          Binding {
            if (Instructions.each[Binding, Boolean](condition(a))) {
              Instructions.each[Binding, Boolean](nextCondition(a))
            } else {
              false
            }
          }
        })
      }

      /**
        * Underlying implementation of [[map]].
        *
        * @note Don't use this method in user code.
        */
      @inline
      def mapBinding[B](f: (A) => Binding[B]): BindingSeq[B] = {
        BindingSeq.this.flatMapBinding { a: A =>
          Binding {
            if (Instructions.each[Binding, Boolean](condition(a))) {
              Constants(Instructions.each[Binding, B](f(a)))
            } else {
              Empty
            }
          }
        }
      }

      /**
        * Underlying implementation of [[flatMap]].
        *
        * @note Don't use this method in user code.
        */
      @inline
      def flatMapBinding[B](f: (A) => Binding[BindingSeq[B]]): BindingSeq[B] = {
        BindingSeq.this.flatMapBinding { a: A =>
          Binding {
            if (Instructions.each[Binding, Boolean](condition(a))) {
              Instructions.each[Binding, BindingSeq[B]](f(a))
            } else {
              Empty
            }
          }
        }
      }

    }

  }

  /**
    * An data binding expression of sequence that never changes.
    *
    * @group expressions
    */
  final class Constants[+A] private (underlying: ConstantsData[A]) extends BindingSeq[A] {

    @inline
    override def value: Seq[A] = underlying

    @deprecated(message = "Use [[value]] instead", since = "11.0.0")
    @inline
    override def get = value

    @inline
    override private[binding] def removePatchedListener(listener: PatchedListener[A]): Unit = {}

    @inline
    override private[binding] def addPatchedListener(listener: PatchedListener[A]): Unit = {}

  }

  object Constants {

    @inline
    def apply[A](elements: A*) = new Constants(toConstantsData(elements))

    @inline
    def upapplySeq[A](constants: Constants[A]) = Some(constants.value)

  }

  /**
    * @group expressions
    */
  object Vars {

    @inline
    def apply[A](initialValues: A*) = new Vars(toCacheData(initialValues))

    @inline
    def empty[A] = new Vars(emptyCacheData[A])

  }

  /**
    * Source sequence of data binding expression.
    *
    * @group expressions
    */
  final class Vars[A] private (private[Binding] var cacheData: HasCache[A]#Cache)
      extends BindingSeq[A]
      with HasCache[A] {

    private[binding] val publisher = new Publisher[PatchedListener[A]]

    /**
      * Returns a [[scala.collection.mutable.Buffer]] that allow you change the content of this [[Vars]].
      *
      * Whenever you change the returned buffer,
      * other binding expressions that depend on this [[Vars]] will be automatically changed.
      *
      * @note This method must not be invoked inside a `@dom` method body.
      */
    @inline
    override def value: Buffer[A] with Proxy = new Proxy

    @deprecated(message = "Use [[value]] instead", since = "11.0.0")
    @inline
    override def get = value

    private[binding] final class Proxy extends Buffer[A] {
      @inline
      override def apply(n: Int): A = {
        getCache(n)
      }

      @inline
      override def update(n: Int, newelem: A): Unit = {
        updateCache(n, newelem)
        for (listener <- publisher) {
          listener.patched(new PatchedEvent(Vars.this, n, SingleSeq(newelem), 1))
        }
      }

      @inline
      override def clear(): Unit = {
        val oldLength = cacheLength
        clearCache()
        val event = new PatchedEvent(Vars.this, 0, Nil, oldLength)
        for (listener <- publisher) {
          listener.patched(event)
        }
      }

      @inline
      override def length: Int = {
        cacheLength
      }

      @inline
      override def remove(n: Int): A = {
        val result = removeCache(n)
        val event = new PatchedEvent(Vars.this, n, Nil, 1)
        for (listener <- publisher) {
          listener.patched(event)
        }
        result
      }

      @inline
      override def ++=(elements: TraversableOnce[A]): this.type = {
        val oldLength = cacheLength
        val seq = appendCache(elements)
        for (listener <- publisher) {
          listener.patched(new PatchedEvent(Vars.this, oldLength, seq, 0))
        }
        Proxy.this
      }

      @inline
      override def +=:(elem: A): this.type = {
        prependCache(elem)
        for (listener <- publisher) {
          listener.patched(new PatchedEvent(Vars.this, 0, SingleSeq(elem), 0))
        }
        Proxy.this
      }

      @inline
      override def +=(elem: A): this.type = {
        val oldLength = cacheLength
        appendCache(elem)
        for (listener <- publisher) {
          listener.patched(new PatchedEvent(Vars.this, oldLength, SingleSeq(elem), 0))
        }
        Proxy.this
      }

      @inline
      override def insertAll(n: Int, elems: Traversable[A]): Unit = {
        val seq = insertCache(n, elems)
        for {
          listener <- publisher
        } {
          listener.patched(new PatchedEvent(Vars.this, n, seq, 0))
        }
      }

      @inline
      override def iterator: Iterator[A] = {
        cacheIterator
      }
    }

    @inline
    override private[binding] def removePatchedListener(listener: PatchedListener[A]): Unit = {
      publisher.unsubscribe(listener)
    }

    @inline
    override private[binding] def addPatchedListener(listener: PatchedListener[A]): Unit = {
      publisher.subscribe(listener)
    }

  }

  /**
    * A [[BindingSeq]] that contains only one element
    *
    * @group expressions
    */
  final case class SingletonBindingSeq[A](upstream: Binding[A]) extends BindingSeq[A] with ChangedListener[A] {

    private[binding] val publisher = new Publisher[PatchedListener[A]]

    override private[binding] def changed(event: ChangedEvent[A]) = {
      val patchedEvent = new PatchedEvent[A](this, 0, SingleSeq(event.newValue), 1)
      for (listener <- publisher) {
        listener.patched(patchedEvent)
      }
    }

    override def length: Constant[Int] = Constant(1)

    @inline
    override private[binding] def value = SingleSeq(upstream.value)

    @inline
    override private[binding] def removePatchedListener(listener: PatchedListener[A]): Unit = {
      publisher.unsubscribe(listener)
      if (publisher.isEmpty) {
        upstream.removeChangedListener(this)
      }
    }

    @inline
    override private[binding] def addPatchedListener(listener: PatchedListener[A]): Unit = {
      if (publisher.isEmpty) {
        upstream.addChangedListener(this)
      }
      publisher.subscribe(listener)
    }

  }

  /**
    * A mechanism that mounts the result of a data binding expression into DOM or other system.
    *
    * @group expressions
    */
  private[Binding] sealed trait MountPoint extends Binding[Unit] {

    private var referenceCount = 0

    protected def mount(): Unit

    protected def unmount(): Unit

    @inline
    override private[binding] def addChangedListener(listener: ChangedListener[Unit]): Unit = {
      if (referenceCount == 0) {
        mount()
      }
      referenceCount += 1
    }

    @inline
    override private[binding] def removeChangedListener(listener: ChangedListener[Unit]): Unit = {
      referenceCount -= 1
      if (referenceCount == 0) {
        unmount()
      }
    }

    @inline
    override private[binding] def value: Unit = ()

  }

  /**
    * A mechanism that mounts the result of a data binding expression of a sequence into DOM or other system.
    *
    * @group expressions
    */
  abstract class MultiMountPoint[-Element](upstream: BindingSeq[Element]) extends MountPoint {

    protected def mount(): Unit = {
      upstream.addPatchedListener(upstreamListener)
      set(upstream.value)
    }

    protected def unmount(): Unit = {
      upstream.removePatchedListener(upstreamListener)
      set(Seq.empty)
    }

    protected def set(children: Seq[Element]): Unit

    protected def splice(from: Int, that: GenSeq[Element], replaced: Int): Unit

    private val upstreamListener = new PatchedListener[Element] {

      @inline
      override private[binding] def patched(upstreamEvent: PatchedEvent[Element]): Unit = {
        splice(upstreamEvent.from, upstreamEvent.that, upstreamEvent.replaced)
      }

    }

  }

  /**
    * A mechanism that mounts the result of a data binding expression of a single value into DOM or other system.
    *
    * Use this class only if you must override [[mount]] or [[unmount]].
    * If you only want to override [[set]], you can use `Binding[Unit] { onUpstreamChange(upstream.bind) }` instead.
    *
    * @group expressions
    */
  abstract class SingleMountPoint[-Value](upstream: Binding[Value]) extends MountPoint {

    protected def set(value: Value): Unit

    protected def mount(): Unit = {
      set(upstream.value)
      upstream.addChangedListener(upstreamListener)
    }

    protected def unmount(): Unit = {
      upstream.removeChangedListener(upstreamListener)
    }

    private val upstreamListener = new ChangedListener[Value] {
      @inline
      override private[binding] def changed(event: ChangedEvent[Value]): Unit = {
        set(event.newValue)
      }
    }

  }

  private[binding] object DummyPatchedListener extends PatchedListener[Any] {
    @inline
    override private[binding] def patched(event: PatchedEvent[Any]): Unit = {}
  }

  private[binding] object DummyChangedListener extends ChangedListener[Any] {
    @inline
    override private[binding] def changed(event: ChangedEvent[Any]): Unit = {}
  }

}

/**
  * A data binding expression that represents a value that automatically recalculates when its dependencies change.
  *
  * You may create a data binding expression via `Binding { ??? }` block or `@dom` annotation.
  *
  * {{{
  * val bindingInt: Binding[Int] = Binding { 100 }
  * }}}
  *
  * {{{
  * @dom val bindingBr: Binding[HTMLBRElement] = <br/>
  * }}}
  *
  * A data binding expression may depend on other binding expressions via [[bind]] method:
  *
  * {{{
  * val bindingString: Binding[String] = bindingInt.bind.toString
  * }}}
  *
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
trait Binding[+A] {

  @deprecated(message = "Use [[Binding#bind bind]] instead", since = "7.0.0")
  final def each: A = macro Binding.Macros.bind

  /**
    * Returns the current value of this [[Binding]] and mark the current `@dom` method depend on this [[Binding]].
    *
    * Each time the value changes, in the current `@dom` method,
    * all code after the current `bind` expression will be re-evaluated if the current `@dom` method is [[#watch watch]]ing.
    * However, code in current `@dom` method and before the current `bind` expression will not be re-evaluated.
    * This rule is not applied to DOM nodes created by XHTML literal.
    * A change related to a DOM node does not affect siblings and parents of the node.
    *
    * @note This method must be invoked inside a `@dom` method body.
    */
  final def bind: A = macro Binding.Macros.bind

  @deprecated(message = "Use [[value]] instead", since = "11.0.0")
  private[binding] def get: A = value

  /**
    * Returns the current value of this [[Binding]]
    *
    * @note This method must not be invoked inside a `@dom` method body.
    */
  private[binding] def value: A

  private[binding] def removeChangedListener(listener: Binding.ChangedListener[A]): Unit

  private[binding] def addChangedListener(listener: Binding.ChangedListener[A]): Unit

  /**
    * Enable automatic recalculation.
    *
    * You may invoke this method more than once.
    * Then, when you want to disable automatic recalculation,
    * you must invoke [[#unwatch unwatch]] same times as the number of calls to this method.
    *
    * @note This method is recursive, which means that the dependencies of this [[Binding]] will be watched as well.
    */
  @inline
  final def watch(): Unit = {
    addChangedListener(Binding.DummyChangedListener)
  }

  /**
    * Disable automatic recalculation.
    *
    * @note This method is recursive, which means that the dependencies of this [[Binding]] will be unwatched as well.
    */
  @inline
  final def unwatch(): Unit = {
    removeChangedListener(Binding.DummyChangedListener)
  }

}
