(ns jruby-9k-scratch.pson-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io])
  (:import (org.jruby.embed ScriptingContainer)
           (org.jruby RubyInstanceConfig$CompileMode CompatVersion RubyString RubyHash RubyArray)
           (java.io FileInputStream ByteArrayInputStream)
           (puppetlabs.jackson.unencoded JackedSonMapper)
           (org.apache.commons.io.output ByteArrayOutputStream)
           (org.apache.commons.io IOUtils)
           (jruby_9k_scratch QuoteEscapingInputStreamWrapper QuoteUnescapingInputStreamWrapper)))

(defn test-file
  [f]
  (str "dev-resources/jruby9k_scratch/pson_test/" f))

(defn create-scripting-container
  []
  (let [sc (ScriptingContainer.)]
    (.setCompatVersion sc CompatVersion/RUBY1_9)
    (.setCompileMode sc RubyInstanceConfig$CompileMode/OFF)
    (.setLoadPaths sc ["../../../../ruby/puppet/lib"])
    (let [pson (.runScriptlet
                sc
                (str "require 'puppet/external/pson/pure'\n"
                     "PSON"))
          converter (.runScriptlet
                     sc
                     (str "class Converter\n"
                          "   def self.to_a(a)\n"
                          "      a.to_a\n"
                          "   end\n"
                          "\n"
                          "   def self.to_h(h)\n"
                          "      h.to_h\n"
                          "   end\n"
                          "\n"
                          "   def self.read_file(f)\n"
                          "      File.new(f).read\n"
                          "   end\n"
                          "end\n"
                          "Converter\n"))]
      {:sc sc
       :pson pson
       :converter converter})))

(def scripting-container
  (memoize create-scripting-container))

(defn to-a
  [a]
  (let [{:keys [sc converter]} (scripting-container)]
    (.callMethod sc converter "to_a" a RubyArray)))

(defn to-h
  [h]
  (let [{:keys [sc converter]} (scripting-container)]
    (.callMethod sc converter "to_h" h RubyHash)))

(defn ruby-read-file
  [f]
  (let [{:keys [sc converter]} (scripting-container)
        filepath (test-file f)]
    (.callMethod sc converter "read_file" filepath RubyString)))

(defn to-pson
  [x]
  (let [{:keys [sc pson]} (scripting-container)]
    (.callMethod sc pson "generate" x RubyString)))

(defn from-pson
  [rs]
  (let [{:keys [sc pson]} (scripting-container)]
    (.callMethod sc pson "parse" rs Object)))

(defn pson-string->byte-seq
  [s]
  (seq (.getBytes s)))

(defn jackpson-mapper
  []
  (JackedSonMapper.
   (QuoteEscapingInputStreamWrapper.)
   (QuoteUnescapingInputStreamWrapper.)))

(defn to-jackpson
  [x]
  (let [out (ByteArrayOutputStream.)
        mapper (jackpson-mapper)]
    (.writeValue mapper out x )
    (.toInputStream out)))

(defn jackpson-array-seq
  [a]
  (let [to-seq (fn [is]
                 (when (.markSupported is)
                   (.reset is))
                 (seq (IOUtils/toByteArray is)))]
    (mapv to-seq a)))

(defn from-jackpson
  [serialized-instream]
  (let [mapper (jackpson-mapper)]
    (.readValue mapper serialized-instream)))

(defn jackpson-stream->byte-seq
  [is]
  (seq (IOUtils/toByteArray is)))




(deftest pson-roundtrip
  (testing "Can roundtrip a simple array w/pson"
    (let [a (to-a ["funky" "town"])
          serialized (to-pson a)
          deserialized (from-pson serialized)]
      (is (= a deserialized))))

  (testing "Can roundtrip a simple map w/pson"
    (let [m (to-h {"foo" "fooval"
                   "bar" "barval"})
          serialized (to-pson m)
          deserialized (from-pson serialized)]
      (is (= m deserialized))))

  (testing "Can roundtrip an array with jpeg contents w/pson"
    (let [a (to-a [(ruby-read-file "foo.jpeg")])
          serialized (to-pson a)
          _ (io/copy (.getBytes serialized) (io/file "./target/jpeg-serialized-as-array.pson"))
          deserialized (from-pson serialized)]
      (is (= a deserialized)))))

(deftest jackpson-roundtrip
  (testing "Can roundtrip a simple array w/jackpson"
    (let [a ["funky" "town"]
          serialized (to-jackpson a)
          deserialized (from-jackpson serialized)]
      (is (= a (mapv (fn [is] (IOUtils/toString is "UTF-8"))
                     deserialized)))))

  (testing "Can roundtrip a simple map w/jackpson"
    (let [m {"foo" "fooval"
             "bar" "barval"}
          serialized (to-jackpson m)
          deserialized (from-jackpson serialized)]
      (is (= m (reduce (fn [acc [k v]]
                         (assoc acc k (IOUtils/toString v "UTF-8")))
                       {}
                       deserialized)))))

  (testing "Can roundtrip an array with jpeg contents w/jackpson"
    (let [orig-bytes (IOUtils/toByteArray (FileInputStream. (test-file "foo.jpeg")))
          a [(ByteArrayInputStream. orig-bytes)]
          serialized (to-jackpson a)
          bytes (IOUtils/toByteArray serialized)
          _ (io/copy bytes (io/file "./target/jpeg-serialized-as-array.jackpson"))
          deserialized (from-jackpson (ByteArrayInputStream. bytes))]
      (is (= (jackpson-array-seq a)
             (jackpson-array-seq deserialized))))))

(deftest jackpson-pson-compat-test
  (testing "Simple array serializes and deserializes the same with pson and jackpson"
    (let [a ["funky" "town"]
          pson-serialized (to-pson (to-a a))
          jackpson-serialized-bytes (IOUtils/toByteArray
                                     (to-jackpson a))
          pson-deserialized (from-pson pson-serialized)
          jackpson-deserialized (from-jackpson (ByteArrayInputStream.
                                                jackpson-serialized-bytes))]
      (is (= (seq (.getBytes pson-serialized))
             (seq jackpson-serialized-bytes)))
      (is (= (mapv pson-string->byte-seq pson-deserialized)
             (mapv jackpson-stream->byte-seq jackpson-deserialized)))))

  (testing "Simple map serializes and deserializes the same with pson and jackpson"
    (let [m {"foo" "fooval"
             "bar" "barval"}
          pson-serialized (to-pson (to-h m))
          jackpson-serialized-bytes (IOUtils/toByteArray
                                     (to-jackpson m))
          pson-deserialized (from-pson pson-serialized)
          jackpson-deserialized (from-jackpson (ByteArrayInputStream.
                                                jackpson-serialized-bytes))]
      (is (= (seq (.getBytes pson-serialized))
             (seq jackpson-serialized-bytes)))
      (is (= pson-deserialized
             (reduce (fn [acc [k v]]
                       (assoc acc k (IOUtils/toString v "UTF-8")))
                     {}
                     jackpson-deserialized))))))