import request from 'superagent'
import {EventEmitter} from 'events'
import Flux from 'flux'

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
   'conn:msg':   'conn:msg',
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
      return Promise(function(resolve, reject) {

         if (connstore.isOpen(ref))
            resolve(connstore.conn(ref))

         let conn = new WebSocket("ws://" + window.location.host + "/api/connection")
         conn.onerror = (err) => {
            console.log('ws[' + ref + '] ERROR: ' + error)
            reject(err)
         }

         conn.onopen = () => {
            console.log('ws[' + ref + '] CONNECT')
            resolve(conn)
         }

         conn.onMessage = (msg) => console.log('ws[' + ref + '] MSG', msg)
      })
   },

   sendConnect: function(ref) {
   },

   sendDisconnect: function(ref) {
   },

   getAttrs: function(ref) {
   },

   getSerialNumber: function(ref) {
   },

   getEventLog: function(ref) {
   },
}



class ConnStore extends BaseStore {
   constructor() {
      super()

      this._connections = {}

      this.dispatchToken = Dispatcher.register( (ev) => {
         let action = ev.action;

         switch (action) {
            case Constants['conn:open']:
               break

            case Constants['conn:error']:
               break

            case Constants['conn:close']:
               break
         }
      })
   }

   isOpen(ref) {
      return (this._connections[ref] || {}).state
   }

   conn(ref) {
      return (this._connections[ref] || {}).conn
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
