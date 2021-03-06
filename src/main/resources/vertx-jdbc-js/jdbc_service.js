/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/** @module vertx-jdbc-js/jdbc_service */
var utils = require('vertx-js/util/utils');
var JdbcConnection = require('vertx-jdbc-js/jdbc_connection');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JJdbcService = io.vertx.ext.jdbc.JdbcService;

/**
 The JDBC Service is responsible for obtaining either a <code>JdbcConnection</code> or <code>JdbcTransaction</code>
 which can be used to pass SQL statements to a JDBC driver.

 @class
*/
var JdbcService = function(j_val) {

  var j_jdbcService = j_val;
  var that = this;

  /**
   Returns a connection that can be used to perform SQL operations on. It's important to remember
   to close the connection when you are done, so it is returned to the pool.

   @public
   @param handler {function} 
   */
  this.getConnection = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_jdbcService.getConnection(function(ar) {
      if (ar.succeeded()) {
        handler(new JdbcConnection(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
    } else utils.invalidArgs();
  };

  /**
   Normally invoked by the <code>JdbcServiceVerticle</code> to start the service when deployed.
   This is usually not called by the user.

   @public

   */
  this.start = function() {
    var __args = arguments;
    if (__args.length === 0) {
      j_jdbcService.start();
    } else utils.invalidArgs();
  };

  /**
   Normally invoked by the <code>JdbcServiceVerticle</code> to stop the service when the verticle is stopped/undeployed.
   This is usually not called by the user.

   @public

   */
  this.stop = function() {
    var __args = arguments;
    if (__args.length === 0) {
      j_jdbcService.stop();
    } else utils.invalidArgs();
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_jdbcService;
};

/**

 @memberof module:vertx-jdbc-js/jdbc_service
 @param vertx {Vertx} 
 @param config {Object} 
 @return {JdbcService}
 */
JdbcService.create = function(vertx, config) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && typeof __args[1] === 'object') {
    return new JdbcService(JJdbcService.create(vertx._jdel, utils.convParamJsonObject(config)));
  } else utils.invalidArgs();
};

/**

 @memberof module:vertx-jdbc-js/jdbc_service
 @param vertx {Vertx} 
 @param address {string} 
 @return {JdbcService}
 */
JdbcService.createEventBusProxy = function(vertx, address) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && typeof __args[1] === 'string') {
    return new JdbcService(JJdbcService.createEventBusProxy(vertx._jdel, address));
  } else utils.invalidArgs();
};

// We export the Constructor function
module.exports = JdbcService;