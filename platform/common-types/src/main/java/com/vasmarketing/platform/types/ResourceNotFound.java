package com.vasmarketing.platform.types;

/**
 * The requested resource does not exist for the caller's tenant. Resources of other tenants are
 * reported the same way, so IDs cannot be probed across tenants.
 */
public class ResourceNotFound extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String resourceType;

  public ResourceNotFound(String resourceType, Object id) {
    this.resourceType = resourceType;
    super(resourceType + " " + id + " not found");
  }

  public String resourceType() {
    return resourceType;
  }
}
