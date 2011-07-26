var ImageThumbView = Backbone.View.extend({
   className : "thumb-wrap",
   events: {

   },

   initialize: function(options) {
      this.id = "thumb"+this.model.get('id');
      _.bindAll(this, 'render');
   },

   render: function() {
      this.model.set({ project : window.app.status.currentProject });
      var self = this;
      require(["text!application/templates/image/ImageThumb.tpl.html"], function(tpl) {
         var jsonModel = self.model.toJSON();
         jsonModel.resolution = Math.round(1000*jsonModel.resolution)/1000; //round to third decimal
         $(self.el).html(_.template(tpl, jsonModel));
         //$(self.el).find("#getImageProperties-"+self.model.id).click(function(){self.properties();return false;});
         $(self.el).find("#getImageProperties-"+self.model.id).click(function(){
            new ImagePropertiesView({model : self.model}).render();
            return false;
         });
      });
      return this;
   }
});

