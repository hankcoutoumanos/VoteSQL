package me.javoris767.votesql.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.logging.Level;

import me.javoris767.votesql.VoteSQL;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

public class MetricsLite
{
	private static final int REVISION = 5;
	private static final String BASE_URL = "http://mcstats.org";
	private static final String REPORT_URL = "/report/%s";
	private static final String CONFIG_FILE = "plugins/PluginMetrics/config.yml";
	private static final int PING_INTERVAL = 10;
	private final Plugin plugin;
	private final YamlConfiguration configuration;
	private final File configurationFile;
	private final String guid;
	private final Object optOutLock = new Object();
	private VoteSQL _plugin;
	private volatile int taskId = -1;

	public MetricsLite(VoteSQL plugin) throws IOException
	{
		_plugin = plugin;
		if (_plugin == null)
		{
			throw new IllegalArgumentException("Plugin cannot be null");
		}

		this.plugin = _plugin;

		this.configurationFile = new File("plugins/PluginMetrics/config.yml");
		this.configuration = YamlConfiguration
				.loadConfiguration(this.configurationFile);

		this.configuration.addDefault("opt-out", Boolean.valueOf(false));
		this.configuration.addDefault("guid", UUID.randomUUID().toString());

		if (this.configuration.get("guid", null) == null)
		{
			this.configuration.options().header("http://mcstats.org")
					.copyDefaults(true);
			this.configuration.save(this.configurationFile);
		}

		this.guid = this.configuration.getString("guid");
	}

	public boolean start()
	{
		synchronized (this.optOutLock)
		{
			if (isOptOut())
			{
				return false;
			}

			if (this.taskId >= 0)
			{
				return true;
			}

			this.taskId = this.plugin.getServer().getScheduler()
					.scheduleAsyncRepeatingTask(this.plugin, new Runnable()
					{
						private boolean firstPost = true;

						public void run()
						{
							try
							{
								synchronized (MetricsLite.this.optOutLock)
								{
									if ((MetricsLite.this.isOptOut())
											&& (MetricsLite.this.taskId > 0))
									{
										MetricsLite.this.plugin
												.getServer()
												.getScheduler()
												.cancelTask(
														MetricsLite.this.taskId);
										MetricsLite.this.taskId = -1;
									}

								}

								MetricsLite.this.postPlugin(!this.firstPost);

								this.firstPost = false;
							}
							catch (IOException e)
							{
								Bukkit.getLogger().log(Level.INFO,
										"[Metrics] " + e.getMessage());
							}
						}
					}, 0L, 12000L);

			return true;
		}
	}

	public boolean isOptOut()
	{
		synchronized (this.optOutLock)
		{
			try
			{
				this.configuration.load("plugins/PluginMetrics/config.yml");
			}
			catch (IOException ex)
			{
				Bukkit.getLogger().log(Level.INFO,
						"[Metrics] " + ex.getMessage());
				return true;
			}
			catch (InvalidConfigurationException ex)
			{
				Bukkit.getLogger().log(Level.INFO,
						"[Metrics] " + ex.getMessage());
				return true;
			}
			return this.configuration.getBoolean("opt-out", false);
		}
	}

	public void enable() throws IOException
	{
		synchronized (this.optOutLock)
		{
			if (isOptOut())
			{
				this.configuration.set("opt-out", Boolean.valueOf(false));
				this.configuration.save(this.configurationFile);
			}

			if (this.taskId < 0)
				start();
		}
	}

	public void disable() throws IOException
	{
		synchronized (this.optOutLock)
		{
			if (!isOptOut())
			{
				this.configuration.set("opt-out", Boolean.valueOf(true));
				this.configuration.save(this.configurationFile);
			}

			if (this.taskId > 0)
			{
				this.plugin.getServer().getScheduler().cancelTask(this.taskId);
				this.taskId = -1;
			}
		}
	}

	private void postPlugin(boolean isPing) throws IOException
	{
		PluginDescriptionFile description = this.plugin.getDescription();

		StringBuilder data = new StringBuilder();
		data.append(encode("guid")).append('=').append(encode(this.guid));
		encodeDataPair(data, "version", description.getVersion());
		encodeDataPair(data, "server", Bukkit.getVersion());
		encodeDataPair(data, "players",
				Integer.toString(Bukkit.getServer().getOnlinePlayers().length));
		encodeDataPair(data, "revision", String.valueOf(5));

		if (isPing)
		{
			encodeDataPair(data, "ping", "true");
		}

		URL url = new URL("http://mcstats.org"
				+ String.format("/report/%s", new Object[]
				{
					encode(this.plugin.getDescription().getName())
				}));
		URLConnection connection;
		if (isMineshafterPresent())
			connection = url.openConnection(Proxy.NO_PROXY);
		else
		{
			connection = url.openConnection();
		}

		connection.setDoOutput(true);

		OutputStreamWriter writer = new OutputStreamWriter(
				connection.getOutputStream());
		writer.write(data.toString());
		writer.flush();

		BufferedReader reader = new BufferedReader(new InputStreamReader(
				connection.getInputStream()));
		String response = reader.readLine();

		writer.close();
		reader.close();

		if ((response == null) || (response.startsWith("ERR")))
			throw new IOException(response);
	}

	private boolean isMineshafterPresent()
	{
		try
		{
			Class.forName("mineshafter.MineServer");
			return true;
		}
		catch (Exception e)
		{
		}
		return false;
	}

	private static void encodeDataPair(StringBuilder buffer, String key,
			String value) throws UnsupportedEncodingException
	{
		buffer.append('&').append(encode(key)).append('=')
				.append(encode(value));
	}

	private static String encode(String text)
			throws UnsupportedEncodingException
	{
		return URLEncoder.encode(text, "UTF-8");
	}

	public static String getReportUrl()
	{
		return REPORT_URL;
	}

	public static String getBaseUrl()
	{
		return BASE_URL;
	}

	public static int getRevision()
	{
		return REVISION;
	}

	public static int getPingInterval()
	{
		return PING_INTERVAL;
	}

	public static String getConfigFile()
	{
		return CONFIG_FILE;
	}
}