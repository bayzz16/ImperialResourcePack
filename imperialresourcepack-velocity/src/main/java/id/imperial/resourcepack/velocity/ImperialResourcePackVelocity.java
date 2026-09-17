package id.imperial.resourcepack.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.player.PostLoginEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ResourcePackInfo;
import id.imperial.resourcepack.common.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.io.*; import java.nio.file.*; import java.util.concurrent.*; import java.util.logging.Logger;

@Plugin(id="imperialresourcepack",name="ImperialResourcePack",version="1.0.0",authors={"bayzz16"})
public final class ImperialResourcePackVelocity {
 private final ProxyServer proxy; private final Logger logger; private final Path data; private final ExecutorService worker=Executors.newSingleThreadExecutor(); private final ResourcePackManager manager; private final ResourcePackHost host; private volatile ResourcePackConfig config;
 @Inject public ImperialResourcePackVelocity(ProxyServer proxy,Logger logger,@com.velocitypowered.api.plugin.annotation.DataDirectory Path data){this.proxy=proxy;this.logger=logger;this.data=data;manager=new ResourcePackManager(logger);host=new ResourcePackHost(manager,logger);}
 @Subscribe public void initialize(ProxyInitializeEvent e){reload();proxy.getCommandManager().register("irp",new VelocityCommand(this));logger.info("[ImperialResourcePack] Plugin enabled.");}
 @Subscribe public void shutdown(ProxyShutdownEvent e){host.close();worker.shutdown();}
 @Subscribe public void login(PostLoginEvent e){ResourcePackConfig c=config;if(c!=null&&c.enabled()&&c.deliveryEnabled())proxy.getScheduler().buildTask(this,()->send(e.getPlayer())).delay(c.sendDelayMs(),TimeUnit.MILLISECONDS).schedule();}
 void send(com.velocitypowered.api.proxy.Player player){ResourcePackConfig c=config;ActivePack p=manager.active();if(c==null||p==null||c.publicUrl().isBlank())return;ResourcePackInfo i=proxy.createResourcePackBuilder(c.publicUrl()).setHash(p.sha1()).setId(p.id()).setPrompt(MiniMessage.miniMessage().deserialize(c.prompt())).setShouldForce(c.required()).build();player.sendResourcePackOffer(i);}
 synchronized String reload(){try{Files.createDirectories(data);Path f=data.resolve("config.yml");if(Files.notExists(f))try(InputStream in=getClass().getResourceAsStream("/config.yml")){Files.copy(in,f);}config=ResourcePackConfig.load(f);Path packs=data.resolve(config.packsDirectory()).normalize();if(!packs.startsWith(data))return "Invalid packs-directory.";if(!config.enabled()){host.close();return "Plugin disabled.";}manager.scan(packs,config);host.start(config,worker);if(config.publicUrl().isBlank())logger.warning("[ImperialResourcePack] WARNING: hosting.public-url is empty.");return "Reload complete.";}catch(Exception x){logger.severe("[ImperialResourcePack] ERROR: "+x.getMessage());return "Reload failed: "+x.getMessage();}}
 ResourcePackManager manager(){return manager;}ResourcePackHost host(){return host;}ResourcePackConfig config(){return config;}
}
