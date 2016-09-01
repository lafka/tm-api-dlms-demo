import BaseStore from './BaseStore.js'
import Dispatcher from './Dispatcher'
import {WebSocketConst} from './ws.js'
import _ from 'lodash'

// storage for DLMS objects
class _DLMS extends BaseStore {
   constructor() {
      super()

      this._store = {}

      this.dispatchToken = Dispatcher.register( (ev) => {
         if (ev.action !== WebSocketConst.response)
            return

         let {action, data} = ev,
             {event, type, resource, attributes} = data

         if (type === "dlms" && event === "pubsub") {
            let {operation, attr, result, error, raw} = data.data
            if (operation !== 'read' && operation !== 'read')
               return

            resource = resource.split(/#/)[0]

            if (!this._store[resource])
               this._store[resource] = {}

            this._store[resource][attr] = error ? {error: error} : {value: result, raw: raw}

            this.emitChange()
         } else if (type === "dlms" && attributes) {
            _.each(attributes, ([[res, attr], {raw, result, error}]) =>
               this._store = _.set(this._store, [res, attr], error ? {error: error} : {value: result, raw: raw})
            )
            this.emitChange()
         }
      })
   }

   value(resource, attr) {
      return (this._store[resource] || {})[attr]
   }
}

export default new _DLMS()

