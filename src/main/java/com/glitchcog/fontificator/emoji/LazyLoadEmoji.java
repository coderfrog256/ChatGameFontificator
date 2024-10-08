package com.glitchcog.fontificator.emoji;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;

/**
 * Holds the URL for an emoji image until it is needed, at which point it load it into memory. This is a combination of
 * every possible version of emoji that could be read from its JSON source, be it Twitch V2, Twitch V3, or FrankerFaceZ.
 * For emoji with multiple images, they will have multiple instances of this LazyLoadEmoji object in the emoji<String,
 * LazyLoadEmoji[]> map in EmojiManager.
 * 
 * This object acts as part of an array because Twitch V2 and V3 emoji seem to indicate they'll use multiple instances
 * of emoji as frames of animation. This is as of yet unused. Third party emoji BetterTTV already use animated GIFs.
 * 
 * @author Matt Yanos
 */
public class LazyLoadEmoji
{
    public enum LookupResult
    {
        SUCCESS,
        SUCCESS_GIF,
        FAILURE
    }

    private static final Logger logger = Logger.getLogger(LazyLoadEmoji.class);

    /**
     * Keep track of any failed URLs so we don't clobber the display trying to reload them over and over
     */
    private static List<String> BUSTED_URLS = new ArrayList<String>();

    /**
     * The word or regex that identifies this emoji
     */
    private final String identifier;

    /**
     * Whether this emoji is meant to replace another emoji (like a FrankerFaceZ bot or moderator badge)
     */
    private final String replaces;

    private final EmojiType type;

    private Image image;

    private Image animatedGifImage;

    private URL url;

    private boolean subscriber;

    private String state;

    private int width;

    private int height;

    private boolean animated;

    private boolean animatedGif;

    private static final int DEFAULT_EMOJI_SIZE = 24;

    private boolean firstLoadFailureReported;

    /**
     * Used for FFZ badges only
     */
    private final Color bgColor;

    public LazyLoadEmoji(String id, String url, EmojiType type) throws MalformedURLException
    {
        this(id, null, url, null, type);
    }

    public LazyLoadEmoji(String id, String replaces, String url, Color bgColor, EmojiType type) throws MalformedURLException
    {
        this(id, replaces, url, DEFAULT_EMOJI_SIZE, DEFAULT_EMOJI_SIZE, bgColor, type);
    }

    public LazyLoadEmoji(String identifier, String url, int width, int height, EmojiType type) throws MalformedURLException
    {
        this(identifier, url, width, height, null, type);
    }

    public LazyLoadEmoji(String identifier, String url, int width, int height, Color bgColor, EmojiType type) throws MalformedURLException
    {
        this(identifier, null, url, width, height, bgColor, type);
    }

    public LazyLoadEmoji(String identifier, String replaces, String url, int width, int height, Color bgColor, EmojiType type) throws MalformedURLException
    {
        this.identifier = identifier;
        this.replaces = replaces;
        this.url = new URL(url);
        this.type = type;
        this.width = width;
        this.height = height;
        this.firstLoadFailureReported = false;
        this.bgColor = bgColor;
    }

    public void cacheImage()
    {
        getImage(true);
    }

    /**
     * Lazy-loaded image
     * 
     * @return image
     */
    public Image getImage(boolean animated)
    {
        if (url == null || BUSTED_URLS.contains(url.toString()))
        {
            return null;
        }
        // Lazy load the still image whether or not the emoji is animated
        if (image == null)
        {
            LookupResult result = checkUrl(url);
            if (result == LookupResult.FAILURE)
            {
                BUSTED_URLS.add(url.toString());
                return null;
            }
            else if (result == LookupResult.SUCCESS_GIF)
            {
                animatedGif = true;
            }

            try
            {
                BufferedImage imageFromTwitch = ImageIO.read(url);

                // Hack to make image background transparent because Twitch emote V1 of sizes 2.0 and 3.0 sometimes are
                // not of the correct type for transparency. Kappa (ID 25) is an example of a non transparent emoji in
                // sizes 2.0 and 3.0. Seriously. Download a Kappa size 2.0 image from the V1 URL and open it in an
                // editor. The background is solid, but when Twitch displays it in their chat, it displays transparent.
                if (EmojiOpacityHandler.isCandidateForModification(type, imageFromTwitch.getType(), identifier))
                {
                    image = EmojiOpacityHandler.fixOpaqueEmote(identifier, imageFromTwitch);
                }
                // No hack required
                else
                {
                    image = imageFromTwitch;
                }

                if (image != null)
                {
                    this.width = this.image.getWidth(null);
                    this.height = this.image.getHeight(null);
                }

            }
            catch (IOException e)
            {
                if (!firstLoadFailureReported)
                {
                    logger.error("Unable to load emoji: " + url, e);
                    firstLoadFailureReported = true;
                    image = null;
                }
            }
        }

        // Only lazy load the animated GIF image if the image is an animatedGif type
        if (animatedGif && animatedGifImage == null)
        {
            Dimension dim = new Dimension();
            animatedGifImage = AnimatedGifUtil.loadAnimatedGif(url, dim);
            this.width = (int) dim.getWidth();
            this.height = (int) dim.getHeight();
        }

        // Return the animated GIF image only if animated is requested AND this emoji is an animated GIF
        return animated && animatedGif ? animatedGifImage : image;
    }

    public boolean isSubscriber()
    {
        return subscriber;
    }

    public void setSubscriber(boolean subscriber)
    {
        this.subscriber = subscriber;
    }

    public String getState()
    {
        return state;
    }

    public void setState(String state)
    {
        this.state = state;
    }

    public EmojiType getType()
    {
        return type;
    }

    public int getWidth()
    {
        return width;
    }

    public void setWidth(int width)
    {
        this.width = width;
    }

    public int getHeight()
    {
        return height;
    }

    public void setHeight(int height)
    {
        this.height = height;
    }

    public URL getUrl()
    {
        return url;
    }

    public boolean isAnimated()
    {
        return animated;
    }

    public void setAnimated(boolean animated)
    {
        this.animated = animated;
    }

    public boolean isAnimatedGif()
    {
        return animatedGif;
    }

    public void setAnimatedGif(boolean animatedGif)
    {
        this.animatedGif = animatedGif;
    }

    /**
     * Whether coloring is required, like for FrankerFaceZ emotes that are white and transparent
     * 
     * @return whether coloring is required
     */
    public boolean isColoringRequired()
    {
        return type == EmojiType.FRANKERFACEZ_BADGE || bgColor != null;
    }

    public Color getBgColor()
    {
        return bgColor;
    }

    public boolean isReplacement()
    {
        return replaces != null;
    }

    public String getReplaces()
    {
        return replaces;
    }

    public static LookupResult checkUrl(URL url)
    {
        try
        {
            HttpURLConnection.setFollowRedirects(true); // Does not work for http to https redirects
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("HEAD");
            httpURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.0; en-US; rv:1.9.1.2) Gecko/20090729 Firefox/3.5.2 (.NET CLR 3.5.30729)");
            int responseCode = httpURLConnection.getResponseCode();
            if(responseCode != HttpURLConnection.HTTP_OK)
            {
                return LookupResult.FAILURE;
            }
            String contentType = httpURLConnection.getContentType();
            if (contentType != null && contentType.contains("image/gif"))
            {
                return LookupResult.SUCCESS_GIF;
            }
            return LookupResult.SUCCESS;
        }
        catch (Exception e)
        {
            logger.debug(e.getMessage(), e);
            return LookupResult.FAILURE;
        }
    }
}
