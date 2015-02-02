;; This file provides a sample `user` namespace that shows some things that
;; can be useful during development; especially if you'd like to develop
;; via the REPL.  To use this, simply make a copy of this file named `user.clj`
;; and modify it to your liking.
(ns user
  (:require [puppetlabs.trapperkeeper.config :as config]))

;; The `user_repl.clj` file has utility functions for starting, stopping, and
;; loading Puppet Server from the REPL.  If you define a no-arg function here that returns
;; configuration data for Puppet Server, it will use that config instead of the
;; defaults.  You could just supply a map literal here, but I like to parse
;; the config from my config files on disk, so that I am using the same settings
;; that I would get if I ran the server via `lein run --config`.
(defn puppet-server-conf
  []
  (config/load-config "/Users/jbarlow/puppet-scratch/puppetserver.conf")
  #_(config/load-config "/Users/jbarlow/puppet-scratch/puppetserver-catalog-zero.conf"))

;; The `go` function in the `user-repl` namespace  will start up an instance of
;; Pupppet Server.  This function will load that namespace, switch to it, and call `go`.
;; Here's an example REPL session:
;;
;;    $ lein repl
;;    user=> (go)
;;       2014-10-31 06:20:40,302 INFO  [p.s.j.jruby-puppet-service] Initializing the JRuby service
;;       ...
;;       2014-10-31 06:24:40,036 INFO  [p.s.m.master-service] Puppet Server has successfully started and is now ready to handle requests
;;       ...
;;    user-repl=> (reset)
;;       2014-10-31 06:26:08,639 INFO  [p.t.internal] Beginning shutdown sequence
;;       2014-10-31 06:26:08,640 INFO  [p.t.s.w.jetty9-service] Shutting down web server(s).
;;       ...
;;       2014-10-31 06:26:08,649 INFO  [p.t.internal] Finished shutdown sequence
;;       :reloading (puppetlabs.puppetserver.ringutils puppetlabs.services.master.master-core
;;       ...
;;       2014-10-31 06:26:10,988 INFO  [p.s.j.jruby-puppet-service] Initializing the JRuby service
;;       2014-10-31 06:26:10,990 INFO  [p.t.s.w.jetty9-service] Initializing web server(s).
;;       ...
;;    user-repl=> (stop)
;;       2014-10-31 06:51:20,037 INFO  [p.t.internal] Beginning shutdown sequence
;;       ...
;;       2014-10-31 06:51:20,052 INFO  [p.t.internal] Finished shutdown sequence
;;    user-repl=>
(defn go
  []
  (load-file "./dev/user_repl.clj")
  (ns user-repl)
  ((-> 'user-repl/go resolve deref)))
