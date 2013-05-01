/*
 * +=================================================================+
 * SlipStream Server (WAR)
 * =====
 * Copyright (C) 2013 SixSq Sarl (sixsq.com)
 * =====
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -=================================================================-
 */

$(document).ready(function() {

var labelType, useGradients, nativeTextSupport, animate;

(function() {
  var ua = navigator.userAgent,
      iStuff = ua.match(/iPhone/i) || ua.match(/iPad/i),
      typeOfCanvas = typeof HTMLCanvasElement,
      nativeCanvasSupport = (typeOfCanvas == 'object' || typeOfCanvas == 'function'),
      textSupport = nativeCanvasSupport 
        && (typeof document.createElement('canvas').getContext('2d').fillText == 'function');
  //I'm setting this based on the fact that ExCanvas provides text support for IE
  //and that as of today iPhone/iPad current text support is lame
  labelType = (!nativeCanvasSupport || (textSupport && !iStuff))? 'Native' : 'HTML';
  nativeTextSupport = labelType == 'Native';
  useGradients = nativeCanvasSupport;
  animate = !(iStuff || !nativeCanvasSupport);
})();

    //init data
    var root = {
            id: "Public/BaseImages/Fedora/14.0",
            name: "Public/BaseImages/Fedora/14.0",
            data: { base: true },
            children: [{
                id: "Public/Tutorial/HelloWorld/apache",
                name: "Public/Tutorial/HelloWorld/apache",
                data: {}},{
                id: "Public/Tutorial/HelloWorld/test_client",
                name: "Public/Tutorial/HelloWorld/test_client",
                data: {}}]};

    //Create a new ST instance
    var st = new $jit.ST({
        //id of viz container element
        injectInto: 'infovis',
        //set duration for the animation
        duration: 600,
        //set animation transition type
        transition: $jit.Trans.Quart.easeInOut,
        //set distance between node and its children
        levelDistance: 50,
        //enable panning
        Navigation: {
          enable:true,
          panning:true
        },
		offsetX: 200,
		offsetY: 20,
        //set node and edge styles
        //set overridable=true for styling individual
        //nodes or edges
        Node: {
            height: 60,
            width: 200,
            type: 'rectangle',
            color: 'white',
            overridable: true,
        },
        
        Edge: {
            type: 'bezier',
            overridable: true
        },
	    //Add Tips
	    Tips: {
	      enable: true,
	      onShow: function(tip, node) {
			tip.innerHTML = "";
  		  }
        },
        
        //This method is called on DOM label creation.
        //Use this method to add event handlers and styles to
        //your node.
        onCreateLabel: function(label, node){
            label.class = "dashboard-focus";
            label.id = node.id;
			var idprefix = "dashboard-" + node.name;
			var name = node.name.ellipse(23);
			label.innerHTML = "<div><div class='dashboard-vm-ok' /> \
				<ul style='list-style-type:none'> \
					<li><b>" + name + "</b></li> \
				</ul></div>";

            label.onclick = function(){
            	st.onClick(node.id);
            };

            //set label styles
            var style = label.style;
            style.width = 180 + 'px';
            style.height = 22 + 'px';            
            style.cursor = 'pointer';
            style.color = '#333';
            style.fontSize = '1em';
            style.textAlign= 'left';
            style.paddingLeft = '10px';
            style.paddingTop = '3px';
            
            // Highlight the node corresponding to the current resource
            if(node.data.base === true) {
                style.borderWidth = 1 + 'px';
                style.borderColor = 'black';
            }
            
        },
        
        //This method is called right before plotting
        //an edge. It's useful for changing an individual edge
        //style properties before plotting it.
        //Edge data proprties prefixed with a dollar sign will
        //override the Edge global style properties.
        onBeforePlotLine: function(adj){
            if (adj.nodeFrom.selected && adj.nodeTo.selected) {
                adj.data.$color = "#eed";
                adj.data.$lineWidth = 3;
            }
            else {
                delete adj.data.$color;
                delete adj.data.$lineWidth;
            }
        }
    });
    //load json data
    st.loadJSON(root);
    //compute node positions and layout
    st.compute();
    //optional: make a translation of the tree
//    st.geom.translate(new $jit.Complex(0, 0), "current");
    //emulate a click on the root node.
    st.onClick(st.root);
    //end
})
