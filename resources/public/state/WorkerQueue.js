import BaseStore from './BaseStore'
import Dispatcher from './Dispatcher'
import {WebSocketConst} from './ws.js'

// holds a list of worker that needs to be done
class _WorkerQueue extends BaseStore {
   constructor() {
      super()

      this._queue = {}; // nid => [work, ...]

      this.dispatchToken = Dispatcher.register((ev) => {
         if (ev.action !== WebSocketConst.response)
            return

         let {action, data} = ev,
             {event, type, resource} = data

         if (type !== "queue")
            return

         switch (event) {
            case "pubsub":
               if (type !== "queue")
                  return

               if (!_.isEqual(this._queue[resource], data.queue)) {
                  this._queue[resource] = data.data.queue
                  this.emitChange()
               }
               break;

            default:
               if (type !== "queue" || !data.queue || !data.resource)
                  return

               if (!_.isEqual(this._queue[resource], data.queue)) {
                  this._queue[resource] = data.queue
                  this.emitChange()
               }
               break
         }
      })
   }

   network(nid) {
      return this._queue[nid]
   }

   device(resource) {
      let nid = resource.replace(/\/.*$/, '')
      return _.filter(this._queue[nid], (val) => val[1] === resource)
   }

   attribute(resource, attr) {
      let nid = resource.replace(/\/.*$/, '')
      return _.filter(this._queue[nid],
                     (val) => val[1] === resource && val[2] === attr)
   }
}


export default new _WorkerQueue()
