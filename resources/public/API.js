import request from 'superagent'
import {EventEmitter} from 'events'
import Flux from 'flux'
import _ from 'lodash'

import {Base64Binary} from './Base64.js'

export var Dispatcher = new Flux.Dispatcher()

const http = {
   get: function(url) {
      return new Promise(function(resolve, reject) {
         request
            .get(url)
            .end(function(err, resp) {
               if (resp.status >= 400)
                  reject()
               else
                  resolve(JSON.parse(resp.text))
            })
      })
   },

   post: function(url, data) {
      return new Promise(function(resolve, reject) {
         request
            .post(url)
            .send(data)
            .end(function(resp) {
               if (resp.status >= 400)
                  reject()
               else
                  resolve(JSON.parse(resp.text))
            })
      })
   }
}



export const Constants = {
   'networks:refresh': 'networks:refresh',

   'network:refresh': 'network:refresh',

   'conn:open':  'conn:open',
   'conn:error': 'conn:error',
   'conn:send':  'conn:send',
   'conn:recv':  'conn:recv',
   'conn:close': 'conn:close',
}



export class BaseStore extends EventEmitter {
   emitChange() {
      this.emit('CHANGE')
   }

   addChangeListener(listener) {
      this.on('CHANGE', listener)
   }

   removeChangeListener(listener) {
      this.removeEventListener('CHANGE', listener)
   }
}



export const NetworkActions = {
   listNetworks: function() {
      http
         .get('/api/network')
         .then(function(networks) {

            Dispatcher.dispatch({
               action: Constants['networks:refresh'],
               networks: networks
            })
         })
   },

   getNetwork: function(nid) {
      http
         .get('/api/network/' + nid)
         .then(function(network) {

            Dispatcher.dispatch({
               action: Constants['network:refresh'],
               network: network
            })
         })
   }
}



export const ConnActions = {
   open: function(ref) {
      return new Promise(function(resolve, reject) {

         if (connstore.isOpen(ref))
            resolve(connstore.conn(ref))

         let conn = new WebSocket("ws://" + window.location.host + "/api/connection/" + ref)
         conn.onerror = (err) => {
            console.log('ws[' + ref + '] ERROR: ', err)
            reject(err)
         }

         conn.onopen = () => {
            console.log('ws[' + ref + '] CONNECT')

            Dispatcher.dispatch({
               action: Constants['conn:open'],
               ref: ref,
               conn: conn,
            })

            resolve(conn)
         }

         conn.onclose = () => {
            console.log('ws[' + ref + '] CLOSE')

            Dispatcher.dispatch({
               action: Constants['conn:close'],
               ref: ref,
            })

            // naively try to reconnect
            ConnActions.open(ref)
         }

         conn.onmessage = (msg) => {
            let data = JSON.parse(msg.data)

            Dispatcher.dispatch({
               action: Constants['conn:recv'],
               ref: ref,
               data: _.merge({origin: 'webclient'}, data)
            })
         }
      })
   },

   sendAction: function(ref, action, data) {
      data = data || {}

      if ('open' !== connstore.isOpen(ref)) {
         console.log("trying to send on closed channel")
         return 'closed'
      }

      let conn = connstore.conn(ref)
      conn.send(JSON.stringify(_.merge({ev: action, origin: 'webclient'}, data)))

      Dispatcher.dispatch({
         action: Constants['conn:send'],
         ref: ref,
         data: _.merge({ev: action, origin: 'webclient'}, data)
      })
   },


   readWorker: function(ref, attrs) {
      ConnActions.sendAction(ref, 'read-worker', {attrs})
   },

   writeWorker: function(ref, attrs) {
      ConnActions.sendAction(ref, 'write-worker', {attrs})
   },

   listAttrs: function(ref) {
      ConnActions.sendAction(ref, 'list-attrs')
   },

   readObjectList: function(ref) {
      ConnActions.sendAction(ref, 'object-list')
   }
}



class ConnStore extends BaseStore {
   constructor() {
      super()

      this._connections = {}

      this.dispatchToken = Dispatcher.register( (ev) => {
         let action = ev.action;

         switch (action) {
            case Constants['conn:open']:
               if (!this._connections[ev.ref])
                  this._connections[ev.ref] = {
                     state: null,
                     conn: null,
                     data: []
                  }

               this._connections[ev.ref].state = 'open'
               this._connections[ev.ref].conn = ev.conn

               this.emitChange()
               break

            case Constants['conn:error']:
               break

            case Constants['conn:close']:
               this._connections[ev.ref].state = 'closed'
               this._connections[ev.ref].conn = null

               this.emitChange()
               break

            case Constants['conn:recv']:
            case Constants['conn:send']:
               //this._connections[ev.ref].data.push(ev.data)

               this.emitChange()
               break;
         }
      })
   }

   state(ref) {
      return (this._connections[ref] || {}).state
   }

   isOpen(ref) {
      return (this._connections[ref] || {}).state
   }

   conn(ref) {
      return (this._connections[ref] || {}).conn
   }

   data(ref) {
      return (this._connections[ref] || {}).data
   }
}



let connstore = new ConnStore()
export {connstore as ConnStore}

Dispatcher.register( (ev) => {
} )

class NetworkStore extends BaseStore {
   constructor() {
      super()

      this._networks = {}

      this.dispatchToken = Dispatcher.register( (ev) => {
         let action = ev.action;

         switch (action) {
            case Constants['networks:refresh']:
               this._networks = ev.networks
               this.emitChange()
               break

            case Constants['network:refresh']:
               this._networks[ev.network.key] = ev.network
               this.emitChange()
               break
         }
      })
   }

   network(nid) {
      return this._networks[nid]
   }

   get networks() {
      return this._networks
   }
}

let netstore = new NetworkStore()
export {netstore as NetworkStore}

class DataStore extends BaseStore {
   constructor() {
      super()

      this._resources = {}
      this._futures = {}
      this._pointers= {}

      this.dispatchToken = Dispatcher.register( (ev) => {
         //Constants['data:read']
         //Constants['data:read-worker']
         let action = ev.action

         switch (action) {
            // response to a data:read command
            case Constants['conn:recv']:
               if (ev.data.ev === 'data:update') {
                  this.updateAttr(ev.ref, ev.data.attr, ev.data.data)

                  // remove from the work list
                  if ((this._futures[ev.ref] || {})[ev.data.future])
                     this._futures[ev.ref][ev.data.future] = _.without(this._futures[ev.ref][ev.data.future], ev.data.attr)

               } else if (ev.data.ev === 'data:list') {
                  this._resources[ev.ref] = _.reduce(ev.data.data, function(acc, v) {
                     acc[v[0].slice(1).join('/')] = v[1]
                     return acc
                  }, {})

                  this.emitChange()
               } else if (ev.data.ev === 'data:read-worker') {
                  if ("queue" === ev.data.action) {
                     this._futures[ev.ref] = this._futures[ev.ref] || {}
                     this._futures[ev.ref][ev.data.future] = ev.data.attrs
                     this.emitChange()
                  } else if ("done" === ev.data.action) {
                     delete this._futures[ev.ref][ev.data.future]
                     this.emitChange()
                  }
               } else if (ev.data.ev === 'data:write-worker') {
                  if ("queue" === ev.data.action) {
                     this._futures[ev.ref] = this._futures[ev.ref] || {}
                     this._futures[ev.ref][ev.data.future] = _.map( ev.data.attrs, a =>
                        a.replace(/\/[^/]*$/, '')
                     )
                     this.emitChange()
                  } else if ("done" === ev.data.action) {
                     delete this._futures[ev.ref][ev.data.future]
                     this.emitChange()
                  }
               } else if (ev.data.ev === 'data:read-init') {
                  this._pointers[ev.ref] = this._pointers[ev.ref] || {}
                  this._pointers[ev.ref][ev.data.future] = ev.data.attr
                  this.emitChange()
               } else if (ev.data.ev === 'data:write-init') {
                  this._pointers[ev.ref] = this._pointers[ev.ref] || {}
                  this._pointers[ev.ref][ev.data.future] = ev.data.attr
                  this.emitChange()
               }
               // read responds with the requested data or nil
//
//            case Constants['data:read-worker']:
//               // read-worker just responds with a notification that
//               // there will be a response in the future. Action will
//               // contain field 'future' with a unique reference to
//               // the future response
//
//
//            case Constants['data:update']:
//               // triggered when there was a update to a resource.
//               // The action will contain the fields `data`, `attr`
//               // where `data` contains ALL known values for the field
         }
      })
   }

   updateAttr(ref, attr, data) {
      if (!this._resources[ref])
         this._resources[ref] = {}

      this._resources[ref][attr] = data
      this.emitChange()
   }

   pointers(ref) {
      return this._pointers[ref]
   }

   futures(ref) {
      return this._futures[ref] || {}
   }

   attrs(ref) {
      return this._resources[ref]
   }

   attr(ref, attr, n) {
      return (this._resources[ref] || {})[attr]
   }
}

let datastore = new DataStore()
export {datastore as DataStore}
