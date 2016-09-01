import BaseStore from './BaseStore'
import Dispatcher from './Dispatcher'
import {WebSocketConst} from './ws.js'

// storage for networks/devices
class _Storage extends BaseStore {
   constructor() {
      super()

      this._resources = {}
      this._subscriptions = []

      this.dispatchToken = Dispatcher.register((ev) => {
         if (ev.action !== WebSocketConst.response)
            return

         let {action, data} = ev,
             {event, type, resource} = data

         switch (event) {
            // accept pubsub updates on storage
            case "pubsub":
               if (type === 'lock')
                  this._resources[resource] = _.merge(this._resources[resource], data.data)
               else if (type === 'network')
                  this._resources[resource] = data.data
               else
                  break

               this.emitChange()
               break;

            // accept updates from read requests
            case "storage":
               if (data.devices)       // list of devices
                  _.each(data.devices, (dev) => this._resources[dev.network + '/' + dev.key] = dev)
               else if (data.device)   // single device
                  this._resources[data.device.network + '/' + data.device.key] = data.device
               else if (data.networks) // list of networks
                  _.each(data.networks, (net) => this._resources[net.key] = net)
               else if (data.network)  // single network
                  this._resources[data.network.key] = data.network
               else if (data.subscriptions)  // subscriptions
                  this._subscriptions = data.subscriptions

               this.emitChange()
               break;
         }
      })
   }

   get subscriptions() {
      return this._subscriptions
   }

   get networks() {
      return _.reduce(this._resources, (acc, v, k) => {
         if (null !== k.match(/^[^/]*$/))
            return _.concat(acc, [v])

         return acc
      }, [])
   }


   network(resource) {
      resource = resource.replace(/\/.*/, '')
      return this._resources[resource]
   }


   devices(resource) {
      resource = resource.replace(/\/.*/, '')

      return _.reduce(this._resources, (acc, v, k) => {
         if (null !== k.match(new RegExp("^" + resource + "/[^/]+$")))
            return _.concat(acc, [v])

         return acc
      }, [])
   }
}

export default new _Storage()
