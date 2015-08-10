#### Usage

Download and run `lein install` from this project's root directory.

In your project.clj, add `[clojure-sha-3 "0.1.0"]` to the :dependencies vector.

When using, `:require [joshua-g.sha-3 :as s]` within your `ns` declaration, or `(require '[joshua-g.sha-3 :as s])` from the REPL.

##### Examples

```
user> (require '[joshua-g.sha-3 :as s])
nil
user> (->> (map int "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")
           s/sha-3-512  
           (map (partial format "%02x"))
           (apply str))
"04a371e84ecfb5b8b77cb48610fca8182dd457ce6f326a0fd3d7ec2f1e91636dee691fbe0c985302ba1b0d8dc78c086346b533b49c030d99a27daf1139d6e75e"
```
