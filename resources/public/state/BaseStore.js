import {EventEmitter} from 'events'

export default class BaseStore extends EventEmitter {
   emitChange() {
      this.emit('CHANGE')
   }

   addChangeListener(listener) {
      this.on('CHANGE', listener)
   }

   removeChangeListener(listener) {
      console.log(this)
      this.removeListener('CHANGE', listener)
   }
}
