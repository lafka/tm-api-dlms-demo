import Dispatcher from './Dispatcher'
import BaseStore from './BaseStore'
import _ from 'lodash'

export const WebSocketConst = {
   'response': 'ws:response',
   'open':     'ws:open',
   'error':    'ws:error',
   'closed':   'ws:closed',
}

class _WebSocketConn extends BaseStore {
   constructor() {
      super()

      this._connection = null;

      this.dispatchToken = Dispatcher.register((ev) => {
         let action = ev.action

         switch (action) {
            case WebSocketConst.open:
               this._connection = ev.conn
               this.emitChange()
               break;

            case WebSocketConst.closed:
               this._connection = null
               this.emitChange()
               break;
         }
      })
   }

   closed() {
      return null === this._connection || this._connection.readyState !== WebSocket.OPEN
   }

   get conn() {
      return this._connection
   }
}

const WebSocketConn = new _WebSocketConn();

const WebSocketActions = {
   ping: function() {
      return WebSocketActions.request('ping', {})
   },

   // open a web socket connection to the server
   open: function() {
      return new Promise(function(resolve, reject) {
         let remote = "ws://" + window.location.host + "/ws"
         console.log('ws: opening ' + remote)

         if (!WebSocketConn.closed) {
            resolve(WebSocketConn.conn)
            return
         }

         let conn = new WebSocket(remote)

         conn.onerror = (err) => {
            console.log('ws: ERROR', err)

            Dispatcher.dispatch({
               action: WebSocketConst.error,
               error:  err
            })

            reject(err)
         }

         conn.onopen = () => {
            console.log('ws: OPEN')

            Dispatcher.dispatch({
               action: WebSocketConst.open,
               conn:   conn
            })

            resolve(conn)
         }

         conn.onmessage = (msg) => {
            let data = JSON.parse(msg.data)

            Dispatcher.dispatch({
               action: WebSocketConst.response,
               data:   _.merge({origin: 'websocket'}, data)
            })
         }

         conn.onclose = (reason) => {
            console.log('ws: close', reason)

            Dispatcher.dispatch({
               action: WebSocketConst.closed,
               reason: reason
            })

            var timeout = 5000,
                timer

            const backoff = function() {
               timer = setTimeout(
                  () => WebSocketActions.open()
                           .then((_conn) => {timeout = 5000; clearTimeout(timer)})
                           .catch((_err) => {timeout = Math.min(120000, timeout * 2); backoff()}),
                  timeout)
            }

            backoff()
         }
      })
   },

   close: function() {
      if (WebSocketConn.closed()) {
         return
      }

      WebSocketConn.conn.close(1000, "user")
   },

   request: function(req, data) {
      if (WebSocketConn.closed()) {
         console.log('ws: trying to communicate on closed socket')
         return
      }

      let encoded = JSON.stringify(_.merge({request: req}, data))
      WebSocketConn.conn.send(encoded)
   }
}

export default WebSocketActions
export {WebSocketActions, WebSocketConn}
