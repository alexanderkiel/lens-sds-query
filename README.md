# Lens SDS Query Service

The SDS Query Service is one part of the Study Data Store (SDS). It currently offers one endpoint under `/query` which allows queries in SDQL against the whole data store.

## Environment

Lens SDS Query Service is a [12 Factor App][1] and uses the following environment vars:

* `PORT` - the port to listen on
* `JVM_OPTS` - especially useful for `-Xmx4g`
* `BROKER_HOST` - the host name of the RabbitMQ broker
* `DB_URI` - the Datomic database URI
* `TOKEN_INTROSPECTION_URI` -  the OAuth2 token inspection URI to use
* `DATOMIC_EDITION` - one of `free` or `pro` with a default of `free`

## Usage

FIXME

## Study Data Query Language (SDQL)

A Prismatic Schema is available in the `lens.query` namespace.

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: <http://12factor.net>
