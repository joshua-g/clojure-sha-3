#### Usage

Download and run `lein install` from this project's root directory.

In your project.clj, add `[clojure-sha-3 "0.1.0"]` to the :dependencies vector.

When using, `:require [joshua-g.sha-3 :as s]` within your `ns` declaration, or `(require '[joshua-g.sha-3 :as s])` from the REPL.

##### Examples

```
user> (require '[joshua-g.sha-3 :as s])
nil

user> ; cf. http://www.di-mgt.com.au/sha_testvectors.html
      (->> (repeat 1000000 (int \a))
           ((juxt s/sha-3-224 s/sha-3-256 s/sha-3-384 s/sha-3-512))
           (map #(apply str (map (partial format "%02x") %))))
("d69335b93325192e516a912e6d19a15cb51c6ed5c15243e7a7fd653c" 
 "5c8875ae474a3634ba4fd55ec85bffd661f32aca75c6d699d0cdcb6c115891c1" 
 "eee9e24d78c1855337983451df97c8ad9eedf256c6334f8e948d252d5e0e76847aa0774ddb90a842190d2c558b4b8340" 
 "3c3a876da14034ab60627c077bb98f7e120a2a5370212dffb3385a18d4f38859ed311d0a9d5141ce9cc5c66ee689b266a8aa18ace8282a0e0db596c90b0a7b87")
```
