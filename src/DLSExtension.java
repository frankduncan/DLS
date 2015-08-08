package org.nlogo.extensions.dls;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import java.util.Enumeration;

import org.nlogo.api.Argument;
import org.nlogo.api.CompilerException;
import org.nlogo.api.Context;
import org.nlogo.api.DefaultCommand;
import org.nlogo.api.DefaultReporter;
import org.nlogo.api.Dump;
import org.nlogo.api.ExtensionException;
import org.nlogo.api.ExtensionManager;
import org.nlogo.api.LogoException;
import org.nlogo.api.NumberParser;
import org.nlogo.api.Observer;
import org.nlogo.api.SimpleJobOwner;
import org.nlogo.api.Syntax;

import org.nlogo.app.App;
import org.nlogo.nvm.Workspace;

public class DLSExtension extends org.nlogo.api.DefaultClassManager {
  public void load(org.nlogo.api.PrimitiveManager primManager) {
    primManager.addPrimitive("info", new Info());
    primManager.addPrimitive("report", new Report());
    primManager.addPrimitive("command", new Command());

    bootListener();
  }

  @Override public void unload(ExtensionManager pm) throws ExtensionException {
    listener.interrupt();
  }

  public class Info extends DefaultReporter {
    @Override public Syntax getSyntax() { return Syntax.reporterSyntax(Syntax.StringType()); }
    @Override public String getAgentClassString() { return "OTPL"; } 

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      return listener.address + ":" + listener.server.getLocalPort();
    }
  }

  public class Report extends DefaultReporter {
    @Override public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.StringType(), Syntax.StringType()}, Syntax.StringType());
    }
    @Override public String getAgentClassString() { return "OTPL"; } 

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      String resp = callRemote(args[0].getString(), args[1].getString(), true);
      if(resp.toUpperCase().equals("TRUE")) {
        return true;
      } else if(resp.toUpperCase().equals("FALSE")) {
        return false;
      } else if(!NumberParser.parse(resp).isLeft()) {
        return NumberParser.parse(resp).right().get();
      } else {
        return resp;
      }
    }
  }

  public class Command extends DefaultCommand {
    public Syntax getSyntax() { return Syntax.commandSyntax(new int[]{Syntax.StringType(), Syntax.StringType()}); }
    public String getAgentClassString() { return "OTPL"; }

    public void perform(Argument args[], Context context)
        throws ExtensionException, LogoException {
      callRemote(args[0].getString(), args[1].getString(), false);
    }
  }

  private String callRemote(String hostport, String cmd, boolean returnResponse) {
    String host = hostport.split(":")[0];
    String port = hostport.split(":")[1];
    Socket sock = null;

    try {
      sock = new Socket(host, Integer.parseInt(port));
      sock.getOutputStream().write(returnResponse ? 'R' : 'C');
      sock.getOutputStream().write(cmd.getBytes());
      sock.getOutputStream().write('\n');
      sock.getOutputStream().flush();
      if(returnResponse) {
        return new BufferedReader(new InputStreamReader(sock.getInputStream())).readLine();
      }
    } catch(UnknownHostException e) {
      e.printStackTrace();
    } catch(IOException e) {
      e.printStackTrace();
    } finally {
      if(sock != null) {
        try {
          sock.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    return "";
  }

  private Listener listener;
  private void bootListener() {
    this.listener = new Listener();
    this.listener.start();
  }

  private class Listener extends Thread {
    private ServerSocket server = null;
    private String address = null;
    public void run() {
      try {
        if(InetAddress.getLocalHost() instanceof Inet4Address && !InetAddress.getLocalHost().isLoopbackAddress()) {
          address = InetAddress.getLocalHost().getHostAddress();
        } else {
          for (Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements();) {
            NetworkInterface ni = ifaces.nextElement();
            for(InterfaceAddress iaddr : ni.getInterfaceAddresses()) {
              if(iaddr.getAddress() instanceof Inet4Address && !iaddr.getAddress().isLoopbackAddress()) {
                address = iaddr.getAddress().getHostAddress();
              }
            }
          }
        }
        server = new ServerSocket(0);
        while(true) {
          Socket sock = null;
          try {
            Workspace workspace = App.app().workspace();
            sock = server.accept();
            InputStream is = sock.getInputStream();
            int cmdChar = is.read();
            String cmd = new BufferedReader(new InputStreamReader(is)).readLine();
            SimpleJobOwner owner = new SimpleJobOwner("DLS", workspace.world().mainRNG, Observer.class);
            if(cmdChar == 'C') {
              workspace.runCompiledCommands(owner, workspace.compileCommands(cmd));
            } else {
              String response = Dump.logoObject(workspace.runCompiledReporter(new SimpleJobOwner("DLS", workspace.world().mainRNG, Observer.class), workspace.compileReporter(cmd)), true, true);
              OutputStream os = sock.getOutputStream();
              os.write(response.getBytes());
              os.write('\n');
              os.flush();
            }
          } finally {
            if(sock != null) {
              sock.close();
            }
          }
        }
      } catch (CompilerException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
        // Swallow
      } finally {
        if(server != null) {
          try {
            server.close();
          } catch(IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }
}
