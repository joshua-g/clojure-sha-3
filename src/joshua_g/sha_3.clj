(ns ^{:doc "clojure implementation of SHA-3"
      :author "Joshua Greenberg"}
  joshua-g.sha-3
  (:require [hiphip.long :as hl])) ; for mutable array sugar

(set! *unchecked-math* true)

(declare keccak-1600)
(declare round-1600)
(declare pad-input)
(declare split-input-into-words)
(declare word-to-little-endian-bytes)

(def ^:private sha-3-base-params
  {:domain-suffix 2r10, :suffix-len 2})

(defn- to-hex-string [bytes]
  (apply str (map (partial format "%02x") bytes)))

(defn- sha-3-with-size [size]
  (comp to-hex-string
        (partial keccak-1600
                 (assoc sha-3-base-params :output-size size))))

(def sha-3-224 (sha-3-with-size 224))
(def sha-3-256 (sha-3-with-size 256))
(def sha-3-384 (sha-3-with-size 384))
(def sha-3-512 (sha-3-with-size 512))

(def ^:private round-constants
  [0x0000000000000001 0x0000000000008082 -0x7FFFFFFFFFFF7F76 -0x7FFFFFFF7FFF8000
   0x000000000000808b 0x0000000080000001 -0x7FFFFFFF7FFF7F7F -0x7FFFFFFFFFFF7FF7
   0x000000000000008a 0x0000000000000088 0x0000000080008009 0x000000008000000a
   0x000000008000808b -0x7FFFFFFFFFFFFF75 -0x7FFFFFFFFFFF7F77 -0x7FFFFFFFFFFF7FFD
   -0x7FFFFFFFFFFF7FFE -0x7FFFFFFFFFFFFF80 0x000000000000800a -0x7FFFFFFF7FFFFFF6
   -0x7FFFFFFF7FFF7F7F -0x7FFFFFFFFFFF7F80 0x0000000080000001 -0x7FFFFFFF7FFF7FF8])

(def ^:private rotation-offsets
  (long-array
   [0 1 62 28 27 36 44 6 55 20 3 10 43 25 39 41 45 15 21 8 18 2 61 56 14]))

(def ^:private pi-permutation
  (long-array
   [0 6 12 18 24 3 9 10 16 22 1 7 13 19 20 4 5 11 17 23 2 8 14 15 21]))

(defn- calc-bit-rate [params]
  (- 1600 (* 2 (params :output-size))))

(defn keccak-1600 [params byte-coll]
  "Keccak with b = 1600, i.e. 64-bit words and 24 rounds per block"
  {:pre [(coll? byte-coll)]}
  (let
      [init-state (repeat 25 0)
       block-size-in-words  (quot (calc-bit-rate params) 64)
       output-size-in-bytes (quot (params :output-size) 8)

       keccak-f
       (fn [state]
         (reduce round-1600 (long-array state) round-constants))

       absorb-block
       (fn [state block]
         (->> (concat block (repeat 0))
              (map bit-xor state)
              keccak-f))

       squeeze (partial take block-size-in-words)]
    (->> byte-coll
         (pad-input params)
         (split-input-into-words)
         (partition block-size-in-words)
         (reduce absorb-block init-state)
         (iterate keccak-f)
         (mapcat squeeze)
         (mapcat word-to-little-endian-bytes)
         (take output-size-in-bytes))))

(defmacro ^:private positive-rotate [n x]
  `(let [n# (long ~n)
         x# (long ~x)]
     (bit-or
      (bit-shift-left x# n#)
      (unsigned-bit-shift-right x# (- 64 n#)))))

(defmacro ^:private chi-combinator [a b c]
  `(bit-xor (long ~a) (bit-and (long ~c) (bit-not (long ~b)))))

(defmacro ^:private mod5 [x]
  `(unchecked-remainder-int (+ (long ~x) 5) 5))

(defn- round-1600 [state-array round-constant]
  (let [theta
        (fn [sa]
          (let [c (let [c (long-array 5 0)]
                    (hl/doarr [[i w] sa]
                              (hl/aset c (mod5 i)
                                       (bit-xor w (hl/aget c (mod5 i)))))
                    c)
                c-rot (hl/amap [x c] (positive-rotate 1 x))]
            (hl/doarr [[i w] sa]
                      (hl/aset sa i
                               (bit-xor w
                                        (hl/aget c (mod5 (dec i)))
                                        (hl/aget c-rot (mod5 (inc i)))))))
          sa)

        rho
        (fn [sa]
          (hl/doarr [[i w] sa]
                    (hl/aset sa i
                             (positive-rotate (hl/aget rotation-offsets i)
                                              w)))
          sa)

        pi
        (fn [sa]
          (hl/amake [i 25]
                    (hl/aget sa (hl/aget pi-permutation i))))

        chi
        (fn [sa]
          (hl/amake [i 25]
                    (let [m (long (- i (mod5 i)))]
                      (chi-combinator
                       (hl/aget sa i)
                       (hl/aget sa (+ m (mod5 (+ i 1))))
                       (hl/aget sa (+ m (mod5 (+ i 2))))))))

        iota
        (fn [sa]
          (hl/aset sa 0
                   (bit-xor round-constant (hl/aget sa 0)))
          sa)
        ]
    (-> state-array theta rho pi chi iota)))

(defn- pad-input [params input-bytes]
  (let [block-size-in-bytes (quot (calc-bit-rate params) 8)

        padding-bytes
        (fn [message-byte-count]
          (as-> (inc message-byte-count) $
                (mod (- $) block-size-in-bytes)
                (repeat $ 0x00)))

        suffix-and-padding
        (fn [message-byte-count]
          (as-> (params :domain-suffix) $
                (bit-or $
                        (bit-shift-left 1 (params :suffix-len)))
                (apply vector $ (padding-bytes message-byte-count))
                (update $ (dec (count $)) bit-xor 0x80)))

        lazily-pad (fn lp [bs]
                     (let [[block rst] (split-at block-size-in-bytes bs)
                           n (count block)]
                       (if (< n block-size-in-bytes)
                         (concat block (suffix-and-padding n))
                         (lazy-seq
                          (concat block (lp rst))))))
        ]
    (lazily-pad input-bytes)))

(defn- little-endian-bytes-to-word [bs]
  (->> [0 (map vector (range) bs)]
       (apply reduce (fn [x [i b]]
                       (bit-or x
                               (bit-shift-left b (* i 8)))))))

(defn- word-to-little-endian-bytes [w]
  (->> [[] w]
       (iterate (fn [[bs x]]
                  [(conj bs (bit-and 0xFF x))
                   (unsigned-bit-shift-right x 8)]))
       (#(nth % 8))
       first))

(defn- split-input-into-words [bs]
  (->> (partition 8 bs)
       (map little-endian-bytes-to-word)))
