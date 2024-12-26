<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@ page import="org.springframework.context.ApplicationContext,
					com.red5pro.override.ProStream,
					org.springframework.web.context.WebApplicationContext,
					com.infrared5.red5pro.live.Red5ProLive,
					com.red5pro.ice.nio.*,
					com.red5pro.ice.*,
					java.net.InetSocketAddress,
					java.util.Map,
					java.util.Map.Entry,
					java.util.Set,
					java.util.Iterator,										
					com.google.gson.*"%>
<% 

	JsonObject ret = new JsonObject();

	// Closes socket with host address, transport type, and port.
    // icetransport.jsp?close-socket-address={host}&port={int}&type={ "tcp" | "udp" }
	//
	// icetransport.jsp?close-socket-address="127.0.0.1"&type="tcp"&port="12001"	
        if (request.getParameter("close-socket-address") != null) {
        	ret.addProperty("call","close-socket-address"); 
        	JsonObject outer = new JsonObject();
        	outer.addProperty("closed",false);
            outer.addProperty("found",false);
            String address = request.getParameter("close-socket-address");
            ret.addProperty("address",address);
            
           if(request.getParameter("port") != null){
		       String port = request.getParameter("port");
		       ret.addProperty("port",port);
	           if(request.getParameter("type") != null){	        	  
                    String type = request.getParameter("type").toUpperCase();
                    ret.addProperty("type",type);
                    if(IceTransport.getIceHandler().closeSocketByAddress(address, type , Integer.valueOf(port))){
                    	outer.addProperty("closed",true);
                        outer.addProperty("found",true);

                    }
                }else{
             	   ret.addProperty("type","missing");
                }
           }else{
        	   ret.addProperty("port","missing");
           }
   	    ret.add("results", outer);
   	      	    
        }       

	// Closes all sockets with transport type and port on any local address.
    // icetransport.jsp?close-socket={int}&type={ "tcp" | "udp" }
	//
	// icetransport.jsp?close-socket=12000&type="tcp"
        if (request.getParameter("close-socket") != null) {
		    String port = request.getParameter("close-socket");
		    String type = request.getParameter("type").toUpperCase();
	        if(type!= null  && port!=null ){                    
                int count = IceTransport.getIceHandler().closeSocket(type , Integer.valueOf(port));
                ret.addProperty("count",count);
            }
    	    ret.addProperty("call","close-socket");
        }

        if (request.getParameterMap().containsKey("get-bindings")) {
        	int t = 0;
    		JsonArray outer = new JsonArray();
    		
    		Map<String,Set<String>> bindings = IceTransport.getIceHandler().getBindings();
    	    Iterator<Entry<String, Set<String>>> iter = bindings.entrySet().iterator();
    	    while(iter.hasNext()) {
    	    	
    	    	JsonObject container = new JsonObject();
    	    	JsonArray inner = new JsonArray();
    	    	Entry<String, Set<String>> entry = iter.next();
    	    	container.addProperty("acceptorId", entry.getKey());	    	
    	    	for(String e: entry.getValue()){
    	    		inner.add(e.toString());
    	    	}
    	    	container.add("bindings",inner);// entry.getValue().toArray().toString());    	    	
    	    	outer.add(container);
    	    }
    	    ret.add("results", outer);
    	    ret.addProperty("call","get-bindings");
    	    ret.addProperty("count", bindings.size());
        }
        String results = ret.toString();

	response.getOutputStream().write(ret.toString().getBytes());
	
%>