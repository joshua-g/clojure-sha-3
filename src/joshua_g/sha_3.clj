(ns ^{:doc "clojure implementation of SHA-3"
      :author "Joshua Greenberg"}
  joshua-g.sha-3)

(declare keccak-1600)
(declare round-1600)
(declare pad-input)
(declare split-input-into-words)
(declare word-to-little-endian-bytes)
(declare positive-rotate)

(def ^:private param-map
  {:sha-3-512 {:output-size 512
               :rate 576
               :domain-suffix 2r10
               :suffix-len 2}})

(defn sha-3-512 [byte-coll]
  (keccak-1600 (param-map :sha-3-512) byte-coll))

(def ^:private round-constants
  [0x0000000000000001 0x0000000000008082 -0x7FFFFFFFFFFF7F76 -0x7FFFFFFF7FFF8000
   0x000000000000808b 0x0000000080000001 -0x7FFFFFFF7FFF7F7F -0x7FFFFFFFFFFF7FF7
   0x000000000000008a 0x0000000000000088 0x0000000080008009 0x000000008000000a
   0x000000008000808b -0x7FFFFFFFFFFFFF75 -0x7FFFFFFFFFFF7F77 -0x7FFFFFFFFFFF7FFD
   -0x7FFFFFFFFFFF7FFE -0x7FFFFFFFFFFFFF80 0x000000000000800a -0x7FFFFFFF7FFFFFF6
   -0x7FFFFFFF7FFF7F7F -0x7FFFFFFFFFFF7F80 0x0000000080000001 -0x7FFFFFFF7FFF7FF8])

(def ^:private rotation-offsets
  [0 1 62 28 27 36 44 6 55 20 3 10 43 25 39 41 45 15 21 8 18 2 61 56 14])

(def ^:private pi-permutation
  [0 6 12 18 24 3 9 10 16 22 1 7 13 19 20 4 5 11 17 23 2 8 14 15 21])

(defn keccak-1600 [params byte-coll]
  "Keccak with b = 1600, i.e. 64-bit words and 24 rounds per block"
  {:pre [(coll? byte-coll)]}
  (let
      [init-state (vec (repeat 25 0))
       block-size-in-words  (quot (params :rate) 64)
       output-size-in-bytes (quot (params :output-size) 8)

       keccak-f
       (fn [state]
         (reduce round-1600 state round-constants))

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

(defn- round-1600 [state round-constant]
  (let [theta
        (fn [state]
          (->> (partition 5 state)
               (apply map bit-xor)
               (cycle)
               ((fn [c-cycle]
                  (map bit-xor
                       state
                       (drop 4 c-cycle)
                       (drop 1
                             (map (partial positive-rotate 1) c-cycle)))))))

        rho
        (fn [state]
          (mapv positive-rotate rotation-offsets state))

        pi
        (fn [state-vec]
          (reduce (fn [s from-index]
                    (conj s (state-vec from-index)))
                  []
                  pi-permutation))

        chi-combinator
        (fn [a b c]
          (bit-xor a (bit-and c (bit-not b))))

        chi
        (fn [state]
          (->> (partition 5 state)
               (map cycle)
               (mapcat (fn [r]
                         (->> (partition 3 1 r)
                              (map (partial apply chi-combinator))
                              (take 5))))))

        iota
        (fn [[f & R]]
          (cons (bit-xor f round-constant)
                R))
        ]
    (-> state theta rho pi chi iota)))

(defn- pad-input [params input-bytes]
  (let [block-size-in-bytes (quot (params :rate) 8)
        padding-bytes
        (as-> (inc (count input-bytes)) $
              (mod (- $) block-size-in-bytes)
              (repeat $ 0x00))

        suffix-and-padding
        (as-> (params :domain-suffix) $
              (bit-or $
                      (bit-shift-left 1 (params :suffix-len)))
              (apply vector $ padding-bytes)
              (update $ (dec (count $)) (partial bit-xor 0x80)))
        ]
    (concat input-bytes suffix-and-padding)))

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

(defn- hex-string-to-integer [h]
  (->> (split-at 8 h)
       (map (partial apply str))
       (map #(Long/parseLong % 16))
       ((fn [[a b]] (bit-or (bit-shift-left a 32)
                            b)))))

(defn- positive-rotate [n x]
  (let [n-mod-64 (mod n 64)]
    (bit-or
     (bit-shift-left x n-mod-64)
     (unsigned-bit-shift-right x (- 64 n-mod-64)))))

(defn- bisect-long [l]
  [(unsigned-bit-shift-right l 32)
   (bit-and l 0xFFFFFFFF)])

(defn- pad-zeros [w s]
  (as-> (format (str "%" w "s") s) $
        (clojure.string/replace $ " " "0")))

(defn- hex [l]
  (->> (bisect-long l)
       (map (partial format "%08x"))
       (apply str)))

(defn- bin [l]
  (->> (bisect-long l)
       (map #(Long/toString % 2))
       (map (partial pad-zeros 32))
       (apply str)))
