package campusmesh.contact;

import campusmesh.model.Peer;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ContactManager manages opportunistic contacts at the application level.
 * In CampusMesh, peers do not maintain persistent end-to-end links.
 * Instead, logical contacts represent intermittent proximity encounters between devices
 * as students or faculty move across campus.
 *
 * NOTE: As per design, ContactManager manages logical connectivity and does
 * not manipulate physical Wi-Fi or OS-level network hardware.
 */
public class ContactManager {
    private final Peer localPeer;
    private final Set<Peer> activeContacts;

    /**
     * Constructs a ContactManager for a given local peer.
     */
    public ContactManager(Peer localPeer) {
        if (localPeer == null) {
            throw new IllegalArgumentException("localPeer cannot be null");
        }
        this.localPeer = localPeer;
        this.activeContacts = ConcurrentHashMap.newKeySet();
    }

    /**
     * Records the start of a contact encounter with a remote peer.
     */
    public boolean startContact(Peer remotePeer) {
        if (remotePeer == null || remotePeer.equals(localPeer)) {
            return false;
        }
        boolean added = activeContacts.add(remotePeer);
        if (added) {
            System.out.printf("[CONTACT] Encounter started: %s <-> %s%n",
                    localPeer.getPeerId(), remotePeer.getPeerId());
        }
        return added;
    }

    /**
     * Records the termination/departure of a contact encounter.
     */
    public boolean endContact(Peer remotePeer) {
        if (remotePeer == null) return false;
        boolean removed = activeContacts.remove(remotePeer);
        if (removed) {
            System.out.printf("[CONTACT] Encounter ended: %s disconnected from %s%n",
                    localPeer.getPeerId(), remotePeer.getPeerId());
        }
        return removed;
    }

    /**
     * Checks if a specific remote peer is currently in active contact.
     */
    public boolean isInContact(Peer remotePeer) {
        return remotePeer != null && activeContacts.contains(remotePeer);
    }

    /**
     * Checks if a peer with a specific peerId is in active contact.
     */
    public boolean isInContact(String peerId) {
        if (peerId == null) return false;
        return activeContacts.stream().anyMatch(p -> p.getPeerId().equalsIgnoreCase(peerId));
    }

    /**
     * Returns an unmodifiable set of all currently active peer contacts.
     */
    public Set<Peer> getActiveContacts() {
        return Collections.unmodifiableSet(activeContacts);
    }

    public Peer getLocalPeer() {
        return localPeer;
    }

    public void clearContacts() {
        activeContacts.clear();
    }

    @Override
    public String toString() {
        return String.format("ContactManager[Local=%s, ActiveContacts=%d]",
                localPeer.getPeerId(), activeContacts.size());
    }
}
