import request from 'superagent'
import {EventEmitter} from 'events'
import Flux from 'flux'

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
            console.log('ws[' + ref + '] ERROR: ' + error)
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
         }

         conn.onmessage = (msg) => {
            console.log('ws[' + ref + '] MSG', msg.data)

            let data = JSON.parse(msg.data)

            Dispatcher.dispatch({
               action: Constants['conn:recv'],
               ref: ref,
               data: _.merge({origin: 'webclient'}, data)
            })
         }
      })
   },

   sendConnect: function(ref) {
      ConnActions.sendAction(ref, 'connect')
   },

   sendDisconnect: function(ref) {
      ConnActions.sendAction(ref, 'disconnect')
   },

   getAttrs: function(ref) {
      ConnActions.sendAction(ref, 'get-attributes')
   },

   getInfo: function(ref) {
      ConnActions.sendAction(ref, 'get-info')
   },

   getData: function(ref) {
      ConnActions.sendAction(ref, 'get-data')
   },

   sendAction: function(ref, action, data) {
      data = data || {}

      if ('open' !== connstore.isOpen(ref))
         return 'closed'

      let conn = connstore.conn(ref)
      conn.send(JSON.stringify(_.merge({ev: action, origin: 'webclient'}, data)))

      Dispatcher.dispatch({
         action: Constants['conn:send'],
         ref: ref,
         data: _.merge({ev: action, origin: 'webclient'}, data)
      })
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
               this._connections[ev.ref].data.push(ev.data)

               this.emitChange()
               break;
         }
      })
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
   console.log('action: ' + ev.action)
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
